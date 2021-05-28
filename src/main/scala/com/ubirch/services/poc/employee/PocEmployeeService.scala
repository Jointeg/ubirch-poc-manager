package com.ubirch.services.poc.employee

import cats.data.EitherT
import com.ubirch.PocConfig
import com.ubirch.db.tables.PocRepository
import com.ubirch.models.poc.{ CustomDataSchemaSetting, Poc }
import com.ubirch.models.pocEmployee.PocEmployee
import com.ubirch.services.poc.employee.GetCertifyConfigError.{ InvalidDataPocType, UnknownPoc }
import monix.eval.Task

import java.util.UUID
import javax.inject.{ Inject, Singleton }

trait PocEmployeeService {
  def getCertifyConfig(pocEmployee: PocEmployee): Task[Either[GetCertifyConfigError, GetCertifyConfigDTO]]
}

@Singleton
class PocEmployeeServiceImpl @Inject() (pocRepository: PocRepository, pocConfig: PocConfig) extends PocEmployeeService {
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
        poc.id.toString,
        poc.pocName,
        logoUrl,
        styleTheme,
        poc.address.toString,
        None,
        DataSchemaSettings(
          dataSchemaId,
          packagingFormat,
          None,
          None
        )
      )
    }
    EitherT.fromOption[Task](dtoOpt, InvalidDataPocType(poc.pocType, poc.id))
  }
}

sealed trait GetCertifyConfigError
object GetCertifyConfigError {
  case class InvalidDataPocType(pocType: String, pocId: UUID) extends GetCertifyConfigError
  case class UnknownPoc(pocId: UUID) extends GetCertifyConfigError
}

case class PocContact(email: String, phone: String, mobile: String, fax: String)
case class DataSchemaSettings(
  dataSchemaId: String,
  packagingFormat: Option[String],
  requiredRole: Option[String],
  customSettings: Option[CustomDataSchemaSetting])
case class GetCertifyConfigDTO(
  pocId: String,
  pocName: String,
  logoUrl: String,
  styleTheme: Option[String],
  pocAddress: String,
  pocContact: Option[PocContact],
  dataSchemaSettings: DataSchemaSettings)
