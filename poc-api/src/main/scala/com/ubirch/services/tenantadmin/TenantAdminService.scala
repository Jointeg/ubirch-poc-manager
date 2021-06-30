package com.ubirch.services.tenantadmin

import cats.Applicative
import cats.data.EitherT
import cats.data.Validated.{ Invalid, Valid }
import com.typesafe.scalalogging.LazyLogging
import cats.syntax.apply._
import com.ubirch.controllers.EndpointHelpers.ActivateSwitch
import com.ubirch.controllers.SwitchActiveError.{
  MissingCertifyUserId,
  NotAllowedError,
  ResourceNotFound,
  UserNotCompleted
}
import com.ubirch.controllers.model.TenantAdminControllerJsonModel.PocAdmin_IN
import com.ubirch.controllers.{ EndpointHelpers, SwitchActiveError, TenantAdminContext }
import cats.syntax.either._
import com.ubirch.PocConfig
import com.ubirch.controllers.AddDeviceCreationTokenRequest
import com.ubirch.controllers.model.TenantAdminControllerJsonModel.Poc_IN
import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.db.tables.{ PocAdminRepository, PocAdminStatusRepository, PocRepository, TenantRepository }
import com.ubirch.models.NOK
import com.ubirch.models.poc._
import com.ubirch.models.tenant._
import com.ubirch.services.CertifyKeycloak
import com.ubirch.services.auth.AESEncryption
import com.ubirch.services.keycloak.users.{ KeycloakUserService, Remove2faTokenKeycloakError }
import com.ubirch.services.poc.{ CertifyUserService, Remove2faTokenFromCertifyUserError }
import com.ubirch.services.tenantadmin.CreateWebIdentInitiateIdErrors.PocAdminRepositoryError
import com.ubirch.services.util.Validator
import com.ubirch.util.PocAuditLogging
import monix.eval.Task
import org.joda.time.DateTime
import org.postgresql.util.PSQLException
import org.scalatra.{ Conflict, InternalServerError, NotFound, Ok }

import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import scala.language.{ existentials, higherKinds }
import scala.util.{ Left, Right }

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

  def getPocForTenant(tenant: Tenant, id: UUID): Task[Either[GetPocForTenantError, Poc]]

  def updatePoc(tenant: Tenant, id: UUID, pocIn: Poc_IN): Task[Either[UpdatePocError, Unit]]

  def getPocAdminForTenant(tenant: Tenant, id: UUID): Task[Either[GetPocAdminForTenantError, (PocAdmin, Poc)]]

  def updatePocAdmin(tenant: Tenant, id: UUID, update: PocAdmin_IN): Task[Either[UpdatePocAdminError, Unit]]

  def createPocAdmin(
    tenant: Tenant,
    tenantAdminContext: TenantAdminContext,
    createPocAdminRequest: CreatePocAdminRequest): Task[Either[CreatePocAdminError, UUID]]

  def remove2FaToken(tenant: Tenant, pocAdminId: UUID): Task[Either[Remove2FaTokenError, Unit]]
}

class DefaultTenantAdminService @Inject() (
  aesEncryption: AESEncryption,
  pocRepository: PocRepository,
  tenantRepository: TenantRepository,
  pocAdminRepository: PocAdminRepository,
  pocAdminStatusRepository: PocAdminStatusRepository,
  keycloakUserService: KeycloakUserService,
  pocConfig: PocConfig,
  certifyUserService: CertifyUserService,
  clock: Clock,
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
          case None                                                                   => Task(ResourceNotFound(pocAdminId).asLeft)
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

  override def getPocForTenant(tenant: Tenant, id: UUID): Task[Either[GetPocForTenantError, Poc]] =
    for {
      maybePoc <- pocRepository.getPoc(id)
      r <- maybePoc match {
        case None => Task.pure(GetPocForTenantError.NotFound(id).asLeft)
        case Some(p) if p.tenantId != tenant.id =>
          Task.pure(GetPocForTenantError.AssignedToDifferentTenant(id, tenant.id).asLeft)
        case Some(p) => Task.pure(p.asRight)
      }
    } yield r

  override def updatePoc(tenant: Tenant, id: UUID, pocIn: Poc_IN): Task[Either[UpdatePocError, Unit]] =
    quillMonixJdbcContext.withTransaction {
      for {
        maybePoc <- pocRepository.getPoc(id)
        r <- maybePoc match {
          case None => Task.pure(UpdatePocError.NotFound(id).asLeft)
          case Some(p) if p.tenantId != tenant.id =>
            Task.pure(UpdatePocError.AssignedToDifferentTenant(id, tenant.id).asLeft)
          case Some(p) if p.status != Completed => Task.pure(UpdatePocError.NotCompleted(id, p.status).asLeft)
          case Some(p) =>
            (for {
              _ <- EitherT(Task(isValidUpdatePocData(pocIn)))
              _ <-
                EitherT(pocRepository.updatePoc(pocIn.copyToPoc(p)).map[Either[UpdatePocError, Unit]](_ => Right(())))
            } yield ()).value
        }
      } yield r
    }

  private def isValidUpdatePocData(poc_IN: Poc_IN): Either[UpdatePocError, Unit] = {
    (
      Validator.validateEmail("Invalid poc manager email", poc_IN.manager.email),
      Validator.validatePhone("Invalid phone number", poc_IN.phone),
      Validator.validatePhone("Invalid poc manager phone number", poc_IN.manager.mobilePhone)
    ).mapN {
      case _ => ()
    }.toEither.leftMap(errors => UpdatePocError.ValidationError(errors.toList.mkString("\n")))
  }

  override def getPocAdminForTenant(
    tenant: Tenant,
    id: UUID): Task[Either[GetPocAdminForTenantError, (PocAdmin, Poc)]] =
    for {
      maybePocAdmin <- pocAdminRepository.getPocAdmin(id)
      pocAdminWithPoc <- maybePocAdmin match {
        case None => Task.pure(GetPocAdminForTenantError.NotFound(id).asLeft)
        case Some(pa) if pa.tenantId != tenant.id =>
          Task.pure(GetPocAdminForTenantError.AssignedToDifferentTenant(id, tenant.id).asLeft)
        case Some(pa) =>
          // PocNotFound won't be thrown here, as long as there is a foreign key constraint on respective tables
          pocRepository.single(pa.pocId).map { p => (pa, p).asRight }
      }
    } yield pocAdminWithPoc

  override def updatePocAdmin(tenant: Tenant, id: UUID, update: PocAdmin_IN): Task[Either[UpdatePocAdminError, Unit]] =
    quillMonixJdbcContext.withTransaction {
      for {
        maybePocAdmin <- pocAdminRepository.getPocAdmin(id)
        r <- maybePocAdmin match {
          case None => Task.pure(UpdatePocAdminError.NotFound(id).asLeft)
          case Some(pa) if pa.tenantId != tenant.id =>
            Task.pure(UpdatePocAdminError.AssignedToDifferentTenant(id, tenant.id).asLeft)
          case Some(pa) if pa.status != Pending => Task.pure(UpdatePocAdminError.InvalidStatus(id, pa.status).asLeft)
          case Some(pa) if !pa.webIdentRequired => Task.pure(UpdatePocAdminError.WebIdentRequired.asLeft)
          case Some(pa) if pa.webIdentInitiateId.isDefined =>
            Task.pure(UpdatePocAdminError.WebIdentInitiateIdAlreadySet.asLeft)
          case Some(pa) => pocAdminRepository.updatePocAdmin(update.copyToPocAdmin(pa)) >> Task.pure(().asRight)
        }
      } yield r
    }

  override def createPocAdmin(
    tenant: Tenant,
    tenantAdminContext: TenantAdminContext,
    createPocAdminRequest: CreatePocAdminRequest): Task[Either[CreatePocAdminError, UUID]] =
    quillMonixJdbcContext.withTransaction {
      (for {
        poc <- EitherT.fromOptionF[Task, CreatePocAdminError, Poc](
          pocRepository.getPoc(createPocAdminRequest.pocId),
          CreatePocAdminError.NotFound(s"pocId is not found: ${createPocAdminRequest.pocId.toString}"))
        _ <- isPocAssignedToTenant[Task, CreatePocAdminError](tenant, poc)(CreatePocAdminError.NotFound(
          s"poc: ${poc.id.toString} is not assigned the tenant: ${tenant.id.toString}"))

        pocAdmin <- createPocAdminObj(poc, tenant, createPocAdminRequest)
        _ <- EitherT.liftF[Task, CreatePocAdminError, UUID](pocAdminRepository.createPocAdmin(pocAdmin))

        pocAdminStatus = PocAdminStatus.init(pocAdmin, poc, pocConfig.pocTypeStaticSpaceNameMap)
        _ <- EitherT.liftF[Task, CreatePocAdminError, Unit](pocAdminStatusRepository.createStatus(pocAdminStatus))
      } yield {
        logAuditByTenantAdmin(
          s"create an admin ${pocAdmin.id} of poc ${pocAdmin.pocId}",
          tenantAdminContext)
        pocAdmin.id
      }).value.onErrorHandleWith {
        case ex: PSQLException if ex.getMessage.contains("duplicate") =>
          Task.pure(
            CreatePocAdminError.InvalidDataError(s"email: ${createPocAdminRequest.email} already exists.").asLeft)
        case ex =>
          logger.error("unexpected error occurred.", ex)
          Task.raiseError(ex)
      }
    }

  override def remove2FaToken(tenant: Tenant, pocAdminId: UUID): Task[Either[Remove2FaTokenError, Unit]] =
    for {
      maybePocAdmin <- pocAdminRepository.getPocAdmin(pocAdminId)
      r <- maybePocAdmin match {
        case None => Task.pure(Remove2FaTokenError.NotFound(pocAdminId).asLeft)
        case Some(pocAdmin) if pocAdmin.tenantId != tenant.id =>
          Task.pure(Remove2FaTokenError.AssignedToDifferentTenant(pocAdminId, tenant.id).asLeft)
        case Some(pocAdmin) if pocAdmin.status != Completed =>
          Task.pure(Remove2FaTokenError.NotCompleted(pocAdminId, pocAdmin.status).asLeft)
        case Some(pocAdmin) => certifyUserService.remove2FAToken(CertifyKeycloak.defaultRealm, pocAdmin)
            .flatMap {
              case Left(e) =>
                Task.pure(Remove2FaTokenError.CertifyServiceError(pocAdminId, e).asLeft)
              case Right(_) =>
                pocAdminRepository.updatePocAdmin(pocAdmin.copy(webAuthnDisconnected =
                  Some(DateTime.parse(clock.instant().toString)))) >>
                  Task.pure(().asRight)
            }
      }
    } yield r

  private def isPocAssignedToTenant[F[_]: Applicative, E](
    tenant: Tenant,
    poc: Poc)(error: => E): EitherT[F, E, Unit] = {
    if (poc.tenantId == tenant.id) {
      EitherT.rightT[F, E](())
    } else {
      EitherT.leftT[F, Unit](error)
    }
  }

  private def createPocAdminObj(
    poc: Poc,
    tenant: Tenant,
    createPocAdminRequest: CreatePocAdminRequest): EitherT[Task, CreatePocAdminError, PocAdmin] = {
    PocAdmin.create(
      poc.id,
      tenant.id,
      createPocAdminRequest.firstName,
      createPocAdminRequest.lastName,
      createPocAdminRequest.email,
      createPocAdminRequest.phone,
      createPocAdminRequest.webIdentRequired,
      createPocAdminRequest.dateOfBirth
    ) match {
      case Valid(pocAdmin) => EitherT.rightT[Task, CreatePocAdminError](pocAdmin)
      case Invalid(errors) => EitherT.leftT[Task, PocAdmin](
          CreatePocAdminError.InvalidDataError(s"the input data is invalid. ${errors.toList.mkString("; ")}"))
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

sealed trait GetPocForTenantError
object GetPocForTenantError {
  case class NotFound(pocId: UUID) extends GetPocForTenantError
  case class AssignedToDifferentTenant(pocId: UUID, tenantId: TenantId) extends GetPocForTenantError
}

sealed trait UpdatePocError
object UpdatePocError {
  case class NotFound(pocId: UUID) extends UpdatePocError
  case class AssignedToDifferentTenant(pocId: UUID, tenantId: TenantId) extends UpdatePocError
  case class NotCompleted(pocId: UUID, status: Status) extends UpdatePocError
  case class ValidationError(message: String) extends UpdatePocError
}

sealed trait GetPocAdminForTenantError
object GetPocAdminForTenantError {
  case class NotFound(pocAdminId: UUID) extends GetPocAdminForTenantError
  case class AssignedToDifferentTenant(pocId: UUID, tenantId: TenantId) extends GetPocAdminForTenantError
}

sealed trait UpdatePocAdminError
object UpdatePocAdminError {
  case class NotFound(pocAdminId: UUID) extends UpdatePocAdminError
  case class AssignedToDifferentTenant(pocAdminId: UUID, tenantId: TenantId) extends UpdatePocAdminError
  case class InvalidStatus(pocAdminId: UUID, status: Status) extends UpdatePocAdminError
  case object WebIdentRequired extends UpdatePocAdminError
  case object WebIdentInitiateIdAlreadySet extends UpdatePocAdminError
}

sealed trait CreatePocAdminError
object CreatePocAdminError {
  case class NotFound(message: String) extends CreatePocAdminError
  case class InvalidDataError(message: String) extends CreatePocAdminError
}

sealed trait Remove2FaTokenError
object Remove2FaTokenError {
  case class NotFound(pocAdminId: UUID) extends Remove2FaTokenError
  case class AssignedToDifferentTenant(pocAdminId: UUID, tenantId: TenantId) extends Remove2FaTokenError
  case class NotCompleted(pocAdminId: UUID, status: Status) extends Remove2FaTokenError
  case class CertifyServiceError(pocAdminId: UUID, e: Remove2faTokenFromCertifyUserError) extends Remove2FaTokenError
}
