package com.ubirch.util

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.InvalidX509Exception
import org.bouncycastle.cert.X509CertificateHolder

import java.io.{ ByteArrayInputStream, InputStreamReader, StringWriter }
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.cert.CertificateFactory
import java.util.Date
import com.ubirch.crypto.utils.Utils
import org.bouncycastle.asn1.x500.{ RDN, X500Name }
import org.bouncycastle.asn1.x500.style.IETFUtils
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.ContentVerifierProvider
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.bouncycastle.util.io.pem.PemReader

import scala.annotation.tailrec
import scala.util.{ Failure, Success, Try }

object CertMaterializer extends LazyLogging {

  private def getRDNs(x500Name: X500Name): List[RDN] = x500Name.getRDNs.toList
  private def rdnToString(rdn: RDN): String = IETFUtils.valueToString(rdn.getFirst.getValue)

  def identifiers(x500Name: X500Name): List[(String, String)] = {
    getRDNs(x500Name)
      .map(x => X500Name.getDefaultStyle.oidToDisplayName(x.getFirst.getType))
      .zip(getRDNs(x500Name).map(x => rdnToString(x)))
      .sortBy(_._1)
  }

  def pemFromEncodedContent(content: String): String = {
    s"""-----BEGIN CERTIFICATE-----
       |${URLDecoder.decode(content, StandardCharsets.UTF_8.name())}
       |-----END CERTIFICATE-----""".stripMargin
  }

  def parse(pem: String): Try[X509CertificateHolder] = {
    (for {
      factory <- Try(CertificateFactory.getInstance("X.509", Utils.SECURITY_PROVIDER_NAME))
      reader <- Try(new PemReader(new InputStreamReader(new ByteArrayInputStream(pem.getBytes()))))
      content <- Try(reader.readPemObject().getContent).recoverWith {
        case e: Exception => Failure(InvalidX509Exception("Can't read PEM Object"))
      }
      certificate <- Try(factory.generateCertificate(new ByteArrayInputStream(content)))
      x509CertificateHolder <- Try(new X509CertificateHolder(certificate.getEncoded))
    } yield {
      x509CertificateHolder
    }).recoverWith {
      case _: Exception => Failure(InvalidX509Exception("Invalid Cert"))
    }
  }

  def verifyChainedCert(x509Holders: Seq[X509CertificateHolder]): Try[Boolean] =
    for {
      verifiedSignature <- verifyChainedSignatures(x509Holders)
      verifiedValidDates <- verifyDates(x509Holders)
    } yield verifiedSignature && verifiedValidDates

  def verifyChainedSignatures(x509Holders: Seq[X509CertificateHolder]): Try[Boolean] = {
    def go(x509Holders: Seq[X509CertificateHolder]): Try[Boolean] = {
      x509Holders.length match {
        case 0 =>
          Failure(InvalidX509Exception("Invalid Signature"))
        case 1 =>
          // Self-signed cert is not acceptable
          logger.info("cert is not chain")
          Success(false)
        case n =>
          (for {
            // this is a short-circuit
            // When an error occurs, the first error is wrapped in Failure
            isChainValid <- Try {
              (0 until n - 1).foldLeft(true) { (curr, i) =>
                val cert = x509Holders(i)
                val issuer = x509Holders(i + 1)
                val result = verifySignature(cert, issuer)
                curr && result.get
              }
            }
          } yield {
            isChainValid
          }).recover {
            case e: Exception =>
              logger.info("fail to verify signature", e)
              throw InvalidX509Exception("Invalid Signature")
          }
      }
    }

    val buffer = scala.collection.mutable.ListBuffer.empty[X509CertificateHolder]

    Try {
      x509Holders.foreach { x =>
        if (!buffer.exists(_.getEncoded.sameElements(x.getEncoded))) buffer += x
        else throw InvalidX509Exception("Invalid Chain")
      }
    }.flatMap { _ =>
      go(buffer.toList)
    }
  }

  def verifySignature(cert: X509CertificateHolder, issuer: X509CertificateHolder): Try[Boolean] =
    for {
      provider <- Try {
        val provider = new org.bouncycastle.jce.provider.BouncyCastleProvider()
        val key = issuer.getSubjectPublicKeyInfo
        val verifierProvider: ContentVerifierProvider =
          new JcaContentVerifierProviderBuilder().setProvider(provider).build(key)
        verifierProvider
      }
      isValid <- Try(cert.isSignatureValid(provider))
    } yield isValid

  private def verifyDate(x509Holder: X509CertificateHolder): Try[Boolean] = Try(x509Holder.isValidOn(new Date()))

  private def verifyDates(x509Holders: Seq[X509CertificateHolder]): Try[Boolean] = Try {
    // this is a short-circuit
    // When an error occurs, the first error is wrapped in Failure
    x509Holders.foldLeft(true) { (curr, x509Holder) =>
      val result = verifyDate(x509Holder)
      curr && result.get
    }
  }

  @throws[InvalidX509Exception]
  def sortCerts(certs: Seq[X509CertificateHolder]): Try[Seq[X509CertificateHolder]] = Try {
    val map = certs.map { certificateHolder =>
      certificateHolder.getSubject.toString -> certificateHolder
    }.toMap
    @tailrec
    def go(currentList: Seq[X509CertificateHolder], length: Int): Seq[X509CertificateHolder] = {
      if (currentList.length >= length) currentList
      else {
        val next = map.getOrElse(currentList.last.getIssuer.toString, throw InvalidX509Exception("Invalid Chain"))
        go(currentList :+ next, length)
      }
    }
    go(certs.headOption.toSeq, certs.length)
  }

}

object CertMaterializerSample {
  def main(args: Array[String]): Unit = {
    val cert =
      CertMaterializer.pemFromEncodedContent("MIIDjjCCAnagAwIBAgIJAKY0mtzrX%2BotMA0GCSqGSIb3DQEBCwUAMEcxCzAJBgNV%0D%0ABAYTAkRFMRAwDgYDVQQIDAdDb2xvZ25lMRQwEgYDVQQKDAt1YmlyY2ggR21iSDEQ%0D%0AMA4GA1UEAwwHSXNzdWluZzAgFw0yMTA2MDkxMzU1MTRaGA8yMTIxMDUxNjEzNTUx%0D%0ANFowRjELMAkGA1UEBhMCREUxEDAOBgNVBAgMB0NvbG9nbmUxFDASBgNVBAoMC3Vi%0D%0AaXJjaCBHbWJIMQ8wDQYDVQQDDAZDbGllbnQwggEiMA0GCSqGSIb3DQEBAQUAA4IB%0D%0ADwAwggEKAoIBAQC4xjQoCmAf4ZApLazvuohQA0aN0oKPpLxGyCW7QztYUJ9w%2Bggs%0D%0AyDJFX%2B86iOGAgkRrwl4GPq0njN%2BWltP10k0cl88mC2Z6hmgl1wo06Lg6BQbEjNI0%0D%0AgM7lu9QEKdhlJeT5NPKSI7%2FHmrSYEK8j0%2BCzSiyBLn%2FfkC9fRfSPdZO%2BU8t1GlNs%0D%0AmVl7sDpKQRGz0BmbtaDCNGGzCozrdFCx30aR0npFLDf0LxYQ3jkMDsgVv1uAmekX%0D%0AS7bENioQjEpqZGgyc%2FiZ9EaPTjJIlzqcZWoddBBmnTuvM1Ik8bLc9ukQMl9a5Uuu%0D%0AWwatbQrayp5rruiGgjnhqmKi8%2FHXzhXim6NRAgMBAAGjfDB6MAwGA1UdEwEB%2FwQC%0D%0AMAAwCwYDVR0PBAQDAgWgMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjAd%0D%0ABgNVHQ4EFgQULf3132pXWtG0RrCs4ImmfnJ%2B2cEwHwYDVR0jBBgwFoAUfhoS1LQT%0D%0AqNxNwpDMDxPuLRPzM4UwDQYJKoZIhvcNAQELBQADggEBAKFkZ4Z%2Bfe1uMWVIXZmo%0D%0ACydMNhej%2FAG4BnBTYHhXhhT1JCQUeNbXhk9O4rmcBUHE2SfIgUesOuIbAfzyDI9o%0D%0Akwwu%2B0TzRBDIk7woqVP3EUqCWrUoiPW356t%2BM5675EU%2FmR7CjPWll4VywJGr4GNl%0D%0AZZFDjrsQc2lMJtZBCKNMhB0b07bwaewXzcB4EHJqOqGpNC5IgMdGkAs6aI%2B2ivma%0D%0AMfQKBpAhAgEgcwD9Z%2B2c7lOTdiB4OgyM6WME4FmtKQZBjDovuK8HXzMBshu%2BWCZg%0D%0AcK2KF6bGCLQ535TkTeVqjcJV9dqV%2BXVCTFmhcuS2WSwg%2Bh08wy%2FQF1ufGDyQpGqz%0D%0A2DE%3D")

    logging(CertMaterializer.parse(cert))
  }

  def analyzeChainWithComma(raw: String) = {
    raw.split(",").foreach { rawPem =>
      logging(CertMaterializer.parse(CertMaterializer.pemFromEncodedContent(rawPem)))
    }
  }

  def logging(x: Try[X509CertificateHolder]): Unit = x match {
    case Failure(exception) =>
      println(exception.getMessage)
      sys.exit(1)
    case Success(cr) =>
      require(cr == cr)
      val sw = new StringWriter()
      val pw = new JcaPEMWriter(sw)
      pw.writeObject(cr)
      pw.flush()
      println(sw.toString)
      println(s"Identifiers: ${CertMaterializer.identifiers(cr.getIssuer)}")
      println(s"SerialNumber: ${cr.getSerialNumber}")
      println(s"Issuer: ${cr.getIssuer}")
      println(s"Subject: ${cr.getSubject}")
      println(s"Not Before: ${cr.getNotBefore}")
      println(s"Not After: ${cr.getNotAfter}")
  }
}
