package com.ubirch.services.poc.employee

import cats.data.EitherT
import com.ubirch.db.tables.{ PocLogoRepository, PocRepository }
import com.ubirch.models.poc.{ CustomDataSchemaSetting, Poc, PocLogo }
import com.ubirch.models.pocEmployee.PocEmployee
import com.ubirch.services.poc.employee.GetCertifyConfigError.{ InvalidDataPocType, UnknownPoc }
import com.ubirch.services.poc.employee.GetPocLogoError.{ LogoNotFoundError, PocNotFoundError }
import monix.eval.Task

import java.util.UUID
import javax.inject.{ Inject, Singleton }

trait PocEmployeeService {
  def getCertifyConfig(pocEmployee: PocEmployee): Task[Either[GetCertifyConfigError, GetCertifyConfigDTO]]

  def getPocLogo(pocId: UUID): Task[Either[GetPocLogoError, PocLogoResponse]]
}

@Singleton
class PocEmployeeServiceImpl @Inject() (pocRepository: PocRepository, pocLogoRepository: PocLogoRepository)
  extends PocEmployeeService {
  // @todo move it in proper place
  val pocTypeMap: Map[String, String] = Map(
    "ub_cust_app" -> "bvdw-certificate",
    "ub_vac_app" -> "vaccination-v3",
    "ub_test_app" -> "corona-test",
    "ub_test_api" -> "corona-test",
    "bmg_vac_app" -> "vaccination-bmg-v2",
    "bmg_vac_api" -> "vaccination-bmg-v2"
  )

  def getCertifyConfig(pocEmployee: PocEmployee): Task[Either[GetCertifyConfigError, GetCertifyConfigDTO]] = {
    (for {
      poc <- EitherT.fromOptionF[Task, GetCertifyConfigError, Poc](
        pocRepository.getPoc(pocEmployee.pocId),
        UnknownPoc(pocEmployee.pocId))
      dto <- toGetCertifyConfigDTO(poc)
    } yield {
      dto
    }).value
  }

  private def toGetCertifyConfigDTO(poc: Poc): EitherT[Task, GetCertifyConfigError, GetCertifyConfigDTO] = {
    val dtoOpt = for {
      dataSchemaId <- pocTypeMap.get(poc.pocType)
      customSetting <- CustomDataSchemaSetting.schemaMap.get(dataSchemaId)
    } yield {
      GetCertifyConfigDTO(
        poc.id.toString,
        poc.pocName,
        poc.logoUrl.map(_.url.toString),
        None,
        poc.address.toString, // @todo define it
        None,
        DataSchemaSetting(
          dataSchemaId,
          Some("CBOR"), // @todo get it properly
          None,
          customSetting
        )
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
  case class InvalidDataPocType(pocType: String, pocId: UUID) extends GetCertifyConfigError
  case class UnknownPoc(pocId: UUID) extends GetCertifyConfigError
}

sealed trait GetPocLogoError
object GetPocLogoError {
  case class PocNotFoundError(pocId: UUID) extends GetPocLogoError
  case class LogoNotFoundError(pocId: UUID) extends GetPocLogoError
}

case class PocContact(email: String, phone: String, mobile: String, fax: String)
case class DataSchemaSetting(
  dataSchemaId: String,
  packagingFormat: Option[String],
  requiredRole: Option[String],
  customSetting: CustomDataSchemaSetting)
case class GetCertifyConfigDTO(
  pocId: String,
  pocName: String,
  logoUrl: Option[String],
  styleTheme: Option[String],
  pocAddress: String,
  pocContact: Option[PocContact],
  dataSchemaSetting: DataSchemaSetting)
