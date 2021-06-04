package com.ubirch.services.poc.employee

import cats.data.EitherT
import com.ubirch.PocConfig
import com.ubirch.db.tables.{ PocLogoRepository, PocRepository }
import com.ubirch.models.poc.{ Completed, CustomDataSchemaSetting, Poc, PocLogo }
import com.ubirch.services.poc.employee.GetCertifyConfigError.{ InvalidDataPocType, PocIsNotCompleted, UnknownPoc }
import com.ubirch.services.poc.employee.GetPocLogoError.{ LogoNotFoundError, PocNotFoundError }
import monix.eval.Task

import java.util.UUID
import javax.inject.{ Inject, Singleton }

trait PocEmployeeService {
  def getCertifyConfig(pocCertifyConfigRequest: PocCertifyConfigRequest)
    : Task[Either[GetCertifyConfigError, GetCertifyConfigDTO]]
  def getPocLogo(pocId: UUID): Task[Either[GetPocLogoError, PocLogoResponse]]
}

@Singleton
class PocEmployeeServiceImpl @Inject() (
  pocRepository: PocRepository,
  pocLogoRepository: PocLogoRepository,
  pocConfig: PocConfig)
  extends PocEmployeeService {

  def getCertifyConfig(pocCertifyConfigRequest: PocCertifyConfigRequest)
    : Task[Either[GetCertifyConfigError, GetCertifyConfigDTO]] = {
    (for {
      poc <- EitherT.fromOptionF[Task, GetCertifyConfigError, Poc](
        pocRepository.getPoc(pocCertifyConfigRequest.pocId),
        UnknownPoc(pocCertifyConfigRequest.pocId))
      _ <- isPocCompleted(poc)
      dto <- toGetCertifyConfigDTO(poc)
    } yield {
      dto
    }).value
  }

  private def isPocCompleted(poc: Poc): EitherT[Task, GetCertifyConfigError, Unit] = {
    if (poc.status == Completed) {
      EitherT.rightT[Task, GetCertifyConfigError](())
    } else {
      EitherT.leftT[Task, Unit](PocIsNotCompleted(poc.id.toString))
    }
  }

  private def toGetCertifyConfigDTO(poc: Poc): EitherT[Task, GetCertifyConfigError, GetCertifyConfigDTO] = {
    val dtoOpt = for {
      dataSchemaId <- pocConfig.pocTypeDataSchemaMap.get(poc.pocType)
    } yield {
      // @todo These mappings are a temporal solution. these will be configured in the future.
      val packagingFormat = if (dataSchemaId.contains("bmg")) Some("CBOR") else Some("UPP")
      val styleTheme =
        if (dataSchemaId.contains("bmg")) Some("theme-bmg-blue")
        else if (dataSchemaId == "vaccination-v3") Some("theme-blue")
        else None

      val logoUrl = s"${pocConfig.pocLogoEndpoint}/${poc.id.toString}"
      GetCertifyConfigDTO(
        poc.externalId,
        poc.pocName,
        logoUrl,
        styleTheme,
        poc.address.toString,
        None,
        Seq(DataSchemaSetting(
          dataSchemaId,
          packagingFormat,
          None,
          None
        ))
      )
    }
    EitherT.fromOption[Task](dtoOpt, InvalidDataPocType(poc.pocType, poc.id))
  }

  override def getPocLogo(pocId: UUID): Task[Either[GetPocLogoError, PocLogoResponse]] = {
    for {
      poc <- pocRepository.getPoc(pocId)
      logo <- pocLogoRepository.getPocLogoById(pocId)
    } yield {
      (poc, logo) match {
        case (Some(poc), Some(logo)) if poc.logoUrl.isDefined =>
          Right(PocLogoResponse(PocLogo.getFileExtension(poc.logoUrl.get.url), logo.img))
        case (Some(poc), _)  => Left(LogoNotFoundError(poc.id))
        case (_, Some(logo)) => Left(PocNotFoundError(logo.pocId))
      }
    }
  }
}

case class PocLogoResponse(fileExtension: String, logo: Array[Byte])

sealed trait GetCertifyConfigError
object GetCertifyConfigError {
  case class PocIsNotCompleted(pocId: String) extends GetCertifyConfigError
  case class InvalidDataPocType(pocType: String, pocId: UUID) extends GetCertifyConfigError
  case class UnknownPoc(pocId: UUID) extends GetCertifyConfigError
}

case class PocContact(email: String, phone: String, mobile: String, fax: String)
case class DataSchemaSetting(
  dataSchemaId: String,
  packagingFormat: Option[String],
  requiredRole: Option[String],
  customSettings: Option[CustomDataSchemaSetting])
case class GetCertifyConfigDTO(
  externalId: String,
  pocName: String,
  logoUrl: String,
  styleTheme: Option[String],
  pocAddress: String,
  pocContact: Option[PocContact],
  dataSchemaSettings: Seq[DataSchemaSetting])

sealed trait GetPocLogoError
object GetPocLogoError {
  case class PocNotFoundError(pocId: UUID) extends GetPocLogoError
  case class LogoNotFoundError(pocId: UUID) extends GetPocLogoError
}

case class PocCertifyConfigRequest(
  pocId: UUID
)
