package com.ubirch.controllers.concerns

import org.scalatra.{ halt, ScalatraBase }

import javax.servlet.http.{ HttpServletRequest, HttpServletResponse }
import cats.syntax.traverse._
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.PocConfig
import com.ubirch.models.NOK
import com.ubirch.util.CertMaterializer
import org.bouncycastle.cert.X509CertificateHolder
import org.scalatra.auth.{ ScentryStrategy, ScentrySupport }

import javax.inject.Inject
import scala.util.{ Failure, Success, Try }

trait X509CertSupport {
  // @todo name
  def authenticate[T](request: HttpServletRequest)(action: => T): T
}

class X509CertSupportImpl @Inject() (pocConfig: PocConfig) extends X509CertSupport {
  private val TLS_HEADER_KEY = "X-Forwarded-Tls-Client-Cert"
  def authenticate[T](request: HttpServletRequest)(action: => T): T = {
    (for {
      x509Certs <- Try(Option(request.getHeader(TLS_HEADER_KEY)).getOrElse(throw new RuntimeException("error")))
      splitX509Certs <-
        x509Certs.split(",").toList.traverse(pem => CertMaterializer.parse(CertMaterializer.pemFromEncodedContent(pem)))
      // @todo add issuerCert
      sortedCerts <- Try(CertMaterializer.sortCerts(splitX509Certs))
      isValid <- CertMaterializer.verifyChainedCert(sortedCerts)
    } yield isValid) match {
      case Success(result) =>
        if (result) action
        // @todo message
        else halt(403, NOK.authenticationError("Forbidden"))
      case Failure(exception) =>
        // @todo message
        halt(403, NOK.authenticationError(exception.getMessage))
    }
  }
}

object X509AuthStrategy {

  implicit def request2X509AuthRequest(r: HttpServletRequest): X509AuthRequest = new X509AuthRequest(r)

  private val TLS_HEADER_KEY = "X-Forwarded-Tls-Client-Cert"

  class X509AuthRequest(r: HttpServletRequest) {
    def param: Option[String] = Option(r.getHeader(TLS_HEADER_KEY))
    def providesAuth: Boolean = param.isDefined
    private val credentials = param.map(_.split(",").toList).getOrElse(Seq.empty[String])
    def pems: Seq[String] = credentials
  }
}

class X509AuthenticationStrategy(protected val app: ScalatraBase, pocConfig: PocConfig)
  extends ScentryStrategy[Seq[X509CertificateHolder]] {
  import X509AuthStrategy.request2X509AuthRequest
  protected def validate(pems: Seq[String]): Option[Seq[X509CertificateHolder]] = (for {
    x509Certs <- pems.toList.traverse(pem => CertMaterializer.parse(CertMaterializer.pemFromEncodedContent(pem)))
    // @todo add issuerCert
    sortedCerts <- Try(CertMaterializer.sortCerts(x509Certs))
    isValid <- CertMaterializer.verifyChainedCert(sortedCerts)
  } yield {
    Some(sortedCerts)
  }).recover {
    case e =>
      // @todo logging
      None
  }.getOrElse(None)

  override def isValid(implicit request: HttpServletRequest): Boolean = request.providesAuth

  override def authenticate()(
    implicit request: HttpServletRequest,
    response: HttpServletResponse): Option[Seq[X509CertificateHolder]] = {
    validate(request.pems)
  }
}

trait X509AuthenticationSupport extends LazyLogging {
  self: ScalatraBase with ScentrySupport[Seq[X509CertificateHolder]] =>

  protected def basicAuth(): Option[Seq[X509CertificateHolder]] = scentry.authenticate("X509")

  protected def authenticated(action: Seq[X509CertificateHolder] => Any)(
    implicit request: HttpServletRequest,
    response: HttpServletResponse): Any = {
    basicAuth() match {
      case Some(value) => action(value)
      case _ =>
        logger.warn("the incoming token is invalid.")
        halt(403, NOK.authenticationError("Forbidden"))
    }
  }
}
