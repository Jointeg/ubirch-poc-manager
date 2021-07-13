package com.ubirch.controllers.concerns

import org.scalatra.halt

import javax.servlet.http.HttpServletRequest
import cats.syntax.traverse._
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.{ InvalidX509Exception, PocConfig }
import com.ubirch.controllers.concerns.HeaderKeys.TLS_HEADER_KEY
import com.ubirch.models.NOK
import com.ubirch.util.CertMaterializer

import java.lang.IllegalArgumentException
import javax.inject.{ Inject, Singleton }
import scala.util.{ Failure, Success, Try }

trait X509CertSupport {
  def withVerification[T](request: HttpServletRequest)(action: => T): T
}

@Singleton
class X509CertSupportImpl @Inject() (pocConfig: PocConfig) extends X509CertSupport with LazyLogging {
  /**
    * Verify incoming X509 client certificate. The certificate is expected as a comma-separated chain cert.
    * As a verification step, the configured issuer certs also are used for the chain verification.
    */
  def withVerification[T](request: HttpServletRequest)(action: => T): T = {
    (for {
      x509Certs <- Try(Option(request.getHeader(TLS_HEADER_KEY)).getOrElse(throw new IllegalArgumentException(
        s"Header is missing (${TLS_HEADER_KEY})")))
      splitX509Certs <-
        x509Certs.split(",").toList.traverse(pem => CertMaterializer.parse(CertMaterializer.pemFromEncodedContent(pem)))
      issuers = splitX509Certs.map(_.getIssuer.toString).flatMap(x => pocConfig.issuerCertMap.get(x).toList).distinct
      _ = if (issuers.isEmpty) throw new IllegalArgumentException(s"Issuer cert is not found.")
      // @todo when a client cert chain includes a full chain, this logic must be adjusted
      chain <- CertMaterializer.sortCerts(splitX509Certs).map(sorted => (sorted ++ issuers).distinct)
      isValid <- CertMaterializer.verifyChainedCert(chain)
    } yield isValid) match {
      case Success(result) =>
        if (result) action
        else {
          logger.warn(
            s"The incoming certificate is invalid. Length of incoming certificate chain is ${countCertificates(request)}")
          halt(403, NOK.authenticationError("Forbidden"))
        }
      case Failure(exception) =>
        logger.warn(
          s"The incoming certificate is invalid. Length of incoming certificate chain is ${countCertificates(request)}",
          exception)
        halt(403, NOK.authenticationError("Forbidden"))
    }
  }

  private def countCertificates(request: HttpServletRequest): Int =
    Option(request.getHeader(TLS_HEADER_KEY)) match {
      case Some(certs) => certs.split(",").length
      case None        => 0
    }
}
