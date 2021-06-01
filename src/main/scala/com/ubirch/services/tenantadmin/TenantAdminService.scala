package com.ubirch.services.tenantadmin

import cats.Applicative
import cats.data.EitherT
import cats.syntax.either._
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.controllers.EndpointHelpers.ActivateSwitch
import com.ubirch.controllers.SwitchActiveError.{
  MissingCertifyUserId,
  NotAllowedError,
  UserNotCompleted,
  UserNotFound
}
import com.ubirch.controllers.{ AddDeviceCreationTokenRequest, EndpointHelpers, SwitchActiveError, TenantAdminContext }
import com.ubirch.controllers.{ AddDeviceCreationTokenRequest, TenantAdminContext }
import cats.syntax.either._
import com.ubirch.controllers.AddDeviceCreationTokenRequest
import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.db.tables.{ PocAdminRepository, PocAdminStatusRepository, PocRepository, TenantRepository }
import com.ubirch.models.poc.{ Completed, PocAdmin, PocAdminStatus }
import com.ubirch.models.tenant._
import com.ubirch.services.CertifyKeycloak
import com.ubirch.services.auth.AESEncryption
import com.ubirch.services.tenantadmin.CreateWebIdentInitiateIdErrors.PocAdminRepositoryError
import com.ubirch.services.keycloak.users.KeycloakUserService
import com.ubirch.util.PocAuditLogging
import monix.eval.Task

import java.util.UUID
import javax.inject.Inject
import scala.language.{ existentials, higherKinds }

trait TenantAdminService {

  def getSimplifiedDeviceInfoAsCSV(tenant: Tenant): Task[String]

  def updateWebIdentId(
    tenant: Tenant,
    tenantAdminContext: TenantAdminContext,
    request: UpdateWebIdentIdRequest): Task[Either[UpdateWebIdentIdError, Unit]]

  def createWebIdentInitiateId(
    tenant: Tenant,
    tenantAdminContext: TenantAdminContext,
    createWebIdentInitiateIdRequest: CreateWebIdentInitiateIdRequest)
    : Task[Either[CreateWebIdentInitiateIdErrors, UUID]]

  def getPocAdminStatus(
    tenant: Tenant,
    pocAdminId: UUID): Task[Either[GetPocAdminStatusErrors, GetPocAdminStatusResponse]]

  def switchActiveForPocAdmin(
    pocAdminId: UUID,
    tenantContext: TenantAdminContext,
    active: ActivateSwitch): Task[Either[SwitchActiveError, Unit]]

  def addDeviceCreationToken(
    tenant: Tenant,
    tenantContext: TenantAdminContext,
    addDeviceToken: AddDeviceCreationTokenRequest): Task[Either[String, Unit]]
}

class DefaultTenantAdminService @Inject() (
  aesEncryption: AESEncryption,
  pocRepository: PocRepository,
  tenantRepository: TenantRepository,
  pocAdminRepository: PocAdminRepository,
  pocAdminStatusRepository: PocAdminStatusRepository,
  keycloakUserService: KeycloakUserService,
  quillMonixJdbcContext: QuillMonixJdbcContext)
  extends TenantAdminService
  with PocAuditLogging
  with LazyLogging {

  private val simplifiedDeviceInfoCSVHeader = """"externalId"; "pocName"; "deviceId""""

  override def getSimplifiedDeviceInfoAsCSV(tenant: Tenant): Task[String] = {
    for {
      devicesInfo <- pocRepository.getPoCsSimplifiedDeviceInfoByTenant(tenant.id)
      devicesInCSVFormat = devicesInfo.map(_.toCSVFormat)
    } yield simplifiedDeviceInfoCSVHeader + "\n" + devicesInCSVFormat.mkString("\n")
  }
  override def createWebIdentInitiateId(
    tenant: Tenant,
    tenantAdminContext: TenantAdminContext,
    createWebIdentInitiateIdRequest: CreateWebIdentInitiateIdRequest)
    : Task[Either[CreateWebIdentInitiateIdErrors, UUID]] = {
    import CreateWebIdentInitiateIdErrors._

    def isWebIdentRequired(tenant: Tenant, pocAdmin: PocAdmin): EitherT[Task, CreateWebIdentInitiateIdErrors, Unit] = {
      if (pocAdmin.webIdentRequired) {
        EitherT.rightT[Task, CreateWebIdentInitiateIdErrors](())
      } else {
        EitherT.leftT[Task, Unit](WebIdentNotRequired(tenant.id, pocAdmin.id))
      }
    }

    def assignWebIdentInitiateId(
      pocAdmin: PocAdmin,
      webIdentInitiateId: UUID): EitherT[Task, CreateWebIdentInitiateIdErrors, Unit] = {
      EitherT(
        pocAdminRepository
          .assignWebIdentInitiateId(pocAdmin.id, webIdentInitiateId)
          .map { _ =>
            logAuditByTenantAdmin(
              s"initiated webIdent with $webIdentInitiateId for admin ${pocAdmin.id} of poc ${pocAdmin.pocId}",
              tenantAdminContext)
            Right(())
          }
          .onErrorRecover {
            case exception: Exception => Left(PocAdminRepositoryError(exception.getMessage))
          })
    }

    def isWebIdentAlreadyInitiated(admin: PocAdmin): EitherT[Task, WebIdentAlreadyInitiated, Unit] =
      EitherT(
        if (admin.webIdentInitiateId.isEmpty) Task(Right(()))
        else Task(Left(WebIdentAlreadyInitiated(admin.webIdentInitiateId.get)))
      )

    (for {
      pocAdmin <-
        getPocAdmin(createWebIdentInitiateIdRequest.pocAdminId, CreateWebIdentInitiateIdErrors.PocAdminNotFound)
      _ <-
        isPocAdminAssignedToTenant[Task, CreateWebIdentInitiateIdErrors](tenant, pocAdmin)(
          PocAdminAssignedToDifferentTenant(
            tenant.id,
            pocAdmin.id))
      _ <- isWebIdentAlreadyInitiated(pocAdmin)
      _ <- isWebIdentRequired(tenant, pocAdmin)
      webIdentInitiateId = UUID.randomUUID()
      _ <- assignWebIdentInitiateId(pocAdmin, webIdentInitiateId)
    } yield webIdentInitiateId).value
  }

  override def updateWebIdentId(
    tenant: Tenant,
    tenantAdminContext: TenantAdminContext,
    request: UpdateWebIdentIdRequest): Task[Either[UpdateWebIdentIdError, Unit]] = {
    import UpdateWebIdentIdError._

    def getPocAdminStatus(pocAdmin: PocAdmin): EitherT[Task, UpdateWebIdentIdError, PocAdminStatus] =
      EitherT(
        pocAdminStatusRepository.getStatus(pocAdmin.id).map(_.toRight(NotExistingPocAdminStatus(pocAdmin.id)))
      )

    def updatePocAdminWebIdentIdAndStatus(pocAdmin: PocAdmin) = {
      EitherT.right[UpdateWebIdentIdError](pocAdminRepository.updateWebIdentIdAndStatus(
        request.webIdentId,
        pocAdmin.id)
        .map { _ =>
          logAuditByTenantAdmin(
            s"updated webIdent success with ${request.webIdentId} for admin ${pocAdmin.id} of poc ${pocAdmin.pocId}",
            tenantAdminContext)
          Right(())
        }
        .onErrorRecover {
          case exception: Exception => Left(PocAdminRepositoryError(exception.getMessage))
        })
    }

    def isSameWebIdentInitialId(tenant: Tenant, pocAdmin: PocAdmin) = {
      if (pocAdmin.webIdentInitiateId.contains(request.webIdentInitiateId)) {
        EitherT.rightT[Task, UpdateWebIdentIdError](())
      } else {
        EitherT.leftT[Task, UpdateWebIdentIdError](DifferentWebIdentInitialId(
          request.webIdentInitiateId,
          tenant,
          pocAdmin))
      }
    }

    def isWebIdentAlreadyExist(pocAdmin: PocAdmin) = {
      if (pocAdmin.webIdentId.isDefined) {
        EitherT.leftT[Task, UpdateWebIdentIdError](WebIdentAlreadyExist(pocAdmin.id))
      } else {
        EitherT.rightT[Task, UpdateWebIdentIdError](())
      }
    }

    (for {
      pocAdmin <- getPocAdmin(request.pocAdminId, UnknownPocAdmin)
      _ <- isWebIdentAlreadyExist(pocAdmin)
      _ <- isPocAdminAssignedToTenant[Task, UpdateWebIdentIdError](tenant, pocAdmin)(
        PocAdminIsNotAssignedToRequestingTenant(pocAdmin.tenantId, tenant.id))
      _ <- isSameWebIdentInitialId(tenant, pocAdmin)
      _ <- getPocAdminStatus(pocAdmin)
      _ <- updatePocAdminWebIdentIdAndStatus(pocAdmin)
    } yield ()).value
  }

  private def isPocAdminAssignedToTenant[F[_]: Applicative, E](
    tenant: Tenant,
    pocAdmin: PocAdmin)(error: => E): EitherT[F, E, Unit] = {
    if (pocAdmin.tenantId == tenant.id) {
      EitherT.rightT[F, E](())
    } else {
      EitherT.leftT[F, Unit](error)
    }
  }

  private def getPocAdmin[E](pocAdminId: UUID, error: UUID => E): EitherT[Task, E, PocAdmin] = {
    EitherT(pocAdminRepository.getPocAdmin(pocAdminId).map(_.toRight(error(pocAdminId))))
  }
  override def getPocAdminStatus(
    tenant: Tenant,
    pocAdminId: UUID): Task[Either[GetPocAdminStatusErrors, GetPocAdminStatusResponse]] = {
    import GetPocAdminStatusErrors._
    (for {
      pocAdmin <- getPocAdmin(pocAdminId, adminId => PocAdminNotFound(adminId))
      _ <-
        isPocAdminAssignedToTenant[Task, GetPocAdminStatusErrors](tenant, pocAdmin)(
          PocAdminAssignedToDifferentTenant(
            tenant.id,
            pocAdmin.id))
      pocAdminStatus <-
        EitherT(pocAdminStatusRepository.getStatus(pocAdminId).map(
          _.toRight[GetPocAdminStatusErrors](PocAdminStatusNotFound(pocAdminId))))
    } yield GetPocAdminStatusResponse.fromPocAdminStatus(pocAdminStatus)).value
  }

  override def switchActiveForPocAdmin(
    pocAdminId: UUID,
    tenantContext: TenantAdminContext,
    active: ActivateSwitch): Task[Either[SwitchActiveError, Unit]] = {

    quillMonixJdbcContext.withTransaction {
      pocAdminRepository
        .getPocAdmin(pocAdminId)
        .flatMap {
          case None                                                                   => Task(UserNotFound(pocAdminId).asLeft)
          case Some(admin) if admin.tenantId.value.asJava() != tenantContext.tenantId => Task(NotAllowedError.asLeft)
          case Some(admin) if admin.status != Completed                               => Task(UserNotCompleted.asLeft)
          case Some(admin) if admin.certifyUserId.isEmpty                             => Task(MissingCertifyUserId(pocAdminId).asLeft)

          case Some(admin) =>
            val userId = admin.certifyUserId.get
            (active match {
              case EndpointHelpers.Activate =>
                keycloakUserService.activate(CertifyKeycloak.defaultRealm, userId, CertifyKeycloak)
              case EndpointHelpers.Deactivate =>
                keycloakUserService.deactivate(CertifyKeycloak.defaultRealm, userId, CertifyKeycloak)
            }) >> pocAdminRepository.updatePocAdmin(admin.copy(active = ActivateSwitch.toBoolean(active)))
              .map { _ =>
                logAuditByTenantAdmin(s"$active poc admin ${admin.id} of poc ${admin.pocId}.", tenantContext)
                Right(())
              }
        }
    }
  }

  def addDeviceCreationToken(
    tenant: Tenant,
    tenantContext: TenantAdminContext,
    addDeviceTokenRequest: AddDeviceCreationTokenRequest): Task[Either[String, Unit]] = {

    aesEncryption
      .encrypt(addDeviceTokenRequest.token)(EncryptedDeviceCreationToken(_))
      .flatMap { encryptedDeviceCreationToken: EncryptedDeviceCreationToken =>
        val updatedTenant = tenant.copy(deviceCreationToken = Some(encryptedDeviceCreationToken))
        tenantRepository
          .updateTenant(updatedTenant).map { _ =>
            logAuditByTenantAdmin("updated tenant with new deviceCreationToken", tenantContext)
            Right(())
          }
      }
  }

}

sealed trait CreateWebIdentInitiateIdErrors
object CreateWebIdentInitiateIdErrors {
  case class PocAdminNotFound(pocAdminId: UUID) extends CreateWebIdentInitiateIdErrors
  case class PocAdminAssignedToDifferentTenant(tenantId: TenantId, pocAdminId: UUID)
    extends CreateWebIdentInitiateIdErrors
  case class WebIdentAlreadyInitiated(webIdentInitiateId: UUID) extends CreateWebIdentInitiateIdErrors
  case class WebIdentNotRequired(tenantId: TenantId, pocAdminId: UUID) extends CreateWebIdentInitiateIdErrors
  case class PocAdminRepositoryError(msg: String) extends CreateWebIdentInitiateIdErrors
}

sealed trait UpdateWebIdentIdError
object UpdateWebIdentIdError {
  case class UnknownPocAdmin(id: UUID) extends UpdateWebIdentIdError
  case class WebIdentAlreadyExist(pocAdminId: UUID) extends UpdateWebIdentIdError
  case class PocAdminIsNotAssignedToRequestingTenant(pocAdminTenantId: TenantId, requestingTenantId: TenantId)
    extends UpdateWebIdentIdError
  case class DifferentWebIdentInitialId(requestWebIdentInitialId: UUID, tenant: Tenant, pocAdmin: PocAdmin)
    extends UpdateWebIdentIdError
  case class NotExistingPocAdminStatus(pocAdminId: UUID) extends UpdateWebIdentIdError
}

sealed trait GetPocAdminStatusErrors
object GetPocAdminStatusErrors {
  case class PocAdminNotFound(pocAdminId: UUID) extends GetPocAdminStatusErrors
  case class PocAdminAssignedToDifferentTenant(tenantId: TenantId, pocAdminId: UUID) extends GetPocAdminStatusErrors
  case class PocAdminStatusNotFound(pocAdminId: UUID) extends GetPocAdminStatusErrors
}
