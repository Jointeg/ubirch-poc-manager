package com.ubirch.util

import com.typesafe.scalalogging.LazyLogging
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

// @todo move it
case class InvalidX509Exception(message: String) extends Exception(message)

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
        case e: Exception => throw new Exception("Can't read PEM Object", e)
      }
      certificate <- Try(factory.generateCertificate(new ByteArrayInputStream(content)))
      x509CertificateHolder <- Try(new X509CertificateHolder(certificate.getEncoded))
    } yield {
      x509CertificateHolder
    }).recoverWith {
      case e: Exception => Failure(InvalidX509Exception("Invalid Cert"))
    }
  }

  def verifyChainedCert(x509Holders: Seq[X509CertificateHolder]): Try[Boolean] =
    for {
      verifiedSignature <- verifyChainedSignatures(x509Holders)
      verifiedValidDates <- verifyDates(x509Holders)
    } yield verifiedSignature && verifiedValidDates

  def verifyChainedSignatures(x509Holders: Seq[X509CertificateHolder]): Try[Boolean] = {
    def go(x509Holders: Seq[X509CertificateHolder]) = {
      x509Holders.length match {
        case 0 =>
          Failure(InvalidX509Exception("Invalid Signature"))
        case 1 =>
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
              throw InvalidX509Exception("Invalid Signature")
          }
      }
    }

    val buffer = scala.collection.mutable.ListBuffer.empty[X509CertificateHolder]

    x509Holders.foreach { x =>
      if (!buffer.exists(_.getEncoded.sameElements(x.getEncoded))) buffer += x
      else throw InvalidX509Exception("Invalid Signature")
    }

    go(buffer.toList)

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

  def sortCerts(certs: Seq[X509CertificateHolder]): Seq[X509CertificateHolder] = {
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
    go(Seq(certs.head), certs.length)
  }

}

object CertMaterializerSample {
  def main(args: Array[String]): Unit = {
    val cert =
      CertMaterializer.pemFromEncodedContent("MIIDSzCCAvCgAwIBAgIUGj6aQeNFXEt9lEosZs3s8ZD75JQwCgYIKoZIzj0EAwIwbTELMAkGA1UEBhMCREUxEDAOBgNVBAcMB0NvbG9nbmUxFDASBgNVBAoMC3ViaXJjaCBHbWJIMScwJQYDVQQDDB51YmlyY2ggR3JvdXAgQWNjZXNzIElzc3VpbmcgQ0ExDTALBgNVBC4TBGRlbW8wHhcNMjEwMjI3MTAwNzU2WhcNMjMwMjI3MTAwNzU2WjB1MQswCQYDVQQGEwJERTEQMA4GA1UEBwwHQ29sb2duZTEUMBIGA1UECgwLdWJpcmNoIEdtYkgxLzAtBgNVBAMMJnViaXJjaCBHcm91cCBBY2Nlc3M6IHViaXJjaC12YWMgKGRlbW8pMQ0wCwYDVQQuEwRkZW1vMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAExQSXGrftbOqRgPQKhvegN3Fu9aylZko87HEIcJHGsxp1K49s9zxIB%2FJMAe2Q0Tvcdb4oPEFv90RL%2BPQhirVXCaOCAWQwggFgMB0GA1UdDgQWBBSHLcCurpUfo0H%2FjbcL4PFRtTOruDAfBgNVHSMEGDAWgBQS0eimV8TqnHmROdUuTEhPbAbewTAMBgNVHRMBAf8EAjAAMA4GA1UdDwEB%2FwQEAwIHgDATBgNVHSUEDDAKBggrBgEFBQcDAjBgBggrBgEFBQcBAQRUMFIwUAYIKwYBBQUHMAKGRGh0dHBzOi8vdHJ1c3QudWJpcmNoLmNvbS9kZW1vL2FpYS91YmlyY2hfR3JvdXBfQWNjZXNzX0lzc3VpbmdfQ0EucGVtMFUGA1UdHwROMEwwSqBIoEaGRGh0dHBzOi8vdHJ1c3QudWJpcmNoLmNvbS9kZW1vL2NybC91YmlyY2hfR3JvdXBfQWNjZXNzX0lzc3VpbmdfQ0EucGVtMDIGA1UdEQQrMCmBJ3ViaXJjaC12YWNAZ3JvdXAtYWNjZXNzLmRlbW8udWJpcmNoLmNvbTAKBggqhkjOPQQDAgNJADBGAiEAjF8YfAbLiD9r8NOqCWIaYf365yEIGBfL3zwCo89dyhwCIQCxo18doZC4LHnl0TEWSxqiiScs96rYuPjPJZV31R%2Be7A%3D%3D")
    val cert2 =
      CertMaterializer.pemFromEncodedContent("MIIDSzCCAvCgAwIBAgIUGj6aQeNFXEt9lEosZs3s8ZD75JQwCgYIKoZIzj0EAwIwbTELMAkGA1UEBhMCREUxEDAOBgNVBAcMB0NvbG9nbmUxFDASBgNVBAoMC3ViaXJjaCBHbWJIMScwJQYDVQQDDB51YmlyY2ggR3JvdXAgQWNjZXNzIElzc3VpbmcgQ0ExDTALBgNVBC4TBGRlbW8wHhcNMjEwMjI3MTAwNzU2WhcNMjMwMjI3MTAwNzU2WjB1MQswCQYDVQQGEwJERTEQMA4GA1UEBwwHQ29sb2duZTEUMBIGA1UECgwLdWJpcmNoIEdtYkgxLzAtBgNVBAMMJnViaXJjaCBHcm91cCBBY2Nlc3M6IHViaXJjaC12YWMgKGRlbW8pMQ0wCwYDVQQuEwRkZW1vMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAExQSXGrftbOqRgPQKhvegN3Fu9aylZko87HEIcJHGsxp1K49s9zxIB%2FJMAe2Q0Tvcdb4oPEFv90RL%2BPQhirVXCaOCAWQwggFgMB0GA1UdDgQWBBSHLcCurpUfo0H%2FjbcL4PFRtTOruDAfBgNVHSMEGDAWgBQS0eimV8TqnHmROdUuTEhPbAbewTAMBgNVHRMBAf8EAjAAMA4GA1UdDwEB%2FwQEAwIHgDATBgNVHSUEDDAKBggrBgEFBQcDAjBgBggrBgEFBQcBAQRUMFIwUAYIKwYBBQUHMAKGRGh0dHBzOi8vdHJ1c3QudWJpcmNoLmNvbS9kZW1vL2FpYS91YmlyY2hfR3JvdXBfQWNjZXNzX0lzc3VpbmdfQ0EucGVtMFUGA1UdHwROMEwwSqBIoEaGRGh0dHBzOi8vdHJ1c3QudWJpcmNoLmNvbS9kZW1vL2NybC91YmlyY2hfR3JvdXBfQWNjZXNzX0lzc3VpbmdfQ0EucGVtMDIGA1UdEQQrMCmBJ3ViaXJjaC12YWNAZ3JvdXAtYWNjZXNzLmRlbW8udWJpcmNoLmNvbTAKBggqhkjOPQQDAgNJADBGAiEAjF8YfAbLiD9r8NOqCWIaYf365yEIGBfL3zwCo89dyhwCIQCxo18doZC4LHnl0TEWSxqiiScs96rYuPjPJZV31R%2Be7A%3D%3D")
    val cert3 =
      CertMaterializer.pemFromEncodedContent("MIIDDTCCArSgAwIBAgIUQUzeYx12vzQ4BBiBht0nbDr7ChgwCgYIKoZIzj0EAwIwcjELMAkGA1UEBhMCREUxEDAOBgNVBAcMB0NvbG9nbmUxFDASBgNVBAoMC3ViaXJjaCBHbWJIMSwwKgYDVQQDDCN1YmlyY2ggR3JvdXAgQWNjZXNzIEludGVybWVkaWF0ZSBDQTENMAsGA1UELhMEZGVtbzAeFw0yMTAyMjcwODIyMjBaFw0yMzAyMjcwODIyMjBaMG0xCzAJBgNVBAYTAkRFMRAwDgYDVQQHDAdDb2xvZ25lMRQwEgYDVQQKDAt1YmlyY2ggR21iSDEnMCUGA1UEAwwedWJpcmNoIEdyb3VwIEFjY2VzcyBJc3N1aW5nIENBMQ0wCwYDVQQuEwRkZW1vMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEYhRYYdke9PtQ%2B%2FWwmF0Ujk8WNv972YxKywoHBtHhVXEmwPmUw8DMIC0lbAvERSmgQ7%2FJQgRvIS%2FcVqoS8UyvhKOCASswggEnMB0GA1UdDgQWBBQS0eimV8TqnHmROdUuTEhPbAbewTAfBgNVHSMEGDAWgBTr72fezlcnHGDhonm3zL9v54X8uTASBgNVHRMBAf8ECDAGAQH%2FAgEAMA4GA1UdDwEB%2FwQEAwIBBjBlBggrBgEFBQcBAQRZMFcwVQYIKwYBBQUHMAKGSWh0dHBzOi8vdHJ1c3QudWJpcmNoLmNvbS9kZW1vL2FpYS91YmlyY2hfR3JvdXBfQWNjZXNzX0ludGVybWVkaWF0ZV9DQS5wZW0wWgYDVR0fBFMwUTBPoE2gS4ZJaHR0cHM6Ly90cnVzdC51YmlyY2guY29tL2RlbW8vY3JsL3ViaXJjaF9Hcm91cF9BY2Nlc3NfSW50ZXJtZWRpYXRlX0NBLnBlbTAKBggqhkjOPQQDAgNHADBEAiAS0gUCBf2Mlt5ishVbFafae8Ocy29rv5FvJVZeyVqBaQIgSbiR8%2FtdyBF5OH2lUSuVXNur%2F527K7RZNl2NyMQrBA0%3D")
    val cert4 =
      CertMaterializer.pemFromEncodedContent("MIIDCTCCAq%2BgAwIBAgIUDc4U7rjO2KYSa8s%2FrKwl4o2a4S8wCgYIKoZIzj0EAwIwXTELMAkGA1UEBhMCREUxEDAOBgNVBAcMB0NvbG9nbmUxFDASBgNVBAoMC3ViaXJjaCBHbWJIMRcwFQYDVQQDDA51YmlyY2ggUm9vdCBDQTENMAsGA1UELhMEZGVtbzAeFw0yMTAyMjcwODIxNDJaFw0yNTAyMjcwODIxNDJaMHIxCzAJBgNVBAYTAkRFMRAwDgYDVQQHDAdDb2xvZ25lMRQwEgYDVQQKDAt1YmlyY2ggR21iSDEsMCoGA1UEAwwjdWJpcmNoIEdyb3VwIEFjY2VzcyBJbnRlcm1lZGlhdGUgQ0ExDTALBgNVBC4TBGRlbW8wWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATNwwGEbagduam0jN%2FiyVQgHRy8hmiOtpPDao2tQk8PwiJ88NscPEATGaDhtTNcRgPKvHL8hVg5tpUR1knUfRPDo4IBNjCCATIwHQYDVR0OBBYEFOvvZ97OVyccYOGiebfMv2%2Fnhfy5MBIGA1UdEwEB%2FwQIMAYBAf8CAQEwDgYDVR0PAQH%2FBAQDAgEGMFQGA1UdHgRNMEugIDAegRxncm91cC1hY2Nlc3MuZGVtby51YmlyY2guY29toScwEYIPZGVtby51YmlyY2guY29tMBKCEC5kZW1vLnViaXJjaC5jb20wUAYIKwYBBQUHAQEERDBCMEAGCCsGAQUFBzAChjRodHRwczovL3RydXN0LnViaXJjaC5jb20vZGVtby9haWEvdWJpcmNoX1Jvb3RfQ0EucGVtMEUGA1UdHwQ%2BMDwwOqA4oDaGNGh0dHBzOi8vdHJ1c3QudWJpcmNoLmNvbS9kZW1vL2NybC91YmlyY2hfUm9vdF9DQS5wZW0wCgYIKoZIzj0EAwIDSAAwRQIhAIwnMQSlqcDjy3n0n6iX3gfBoDvEHW24Lok52%2FN2PtVIAiAV%2Bx8b1dWbl2FbPfGUCpe4XP13jRBTezne6co1CtAp2A%3D%3D")
    val cert5 =
      CertMaterializer.pemFromEncodedContent("MIIDFzCCAr2gAwIBAgIUKLqn5R0aLJZwtp8w55oHF2hUfj4wCgYIKoZIzj0EAwIwXTELMAkGA1UEBhMCREUxEDAOBgNVBAcMB0NvbG9nbmUxFDASBgNVBAoMC3ViaXJjaCBHbWJIMRcwFQYDVQQDDA51YmlyY2ggUm9vdCBDQTENMAsGA1UELhMEZGVtbzAeFw0yMTAyMjcwODIxMzNaFw0zNzAyMjcwODIxMzNaMF0xCzAJBgNVBAYTAkRFMRAwDgYDVQQHDAdDb2xvZ25lMRQwEgYDVQQKDAt1YmlyY2ggR21iSDEXMBUGA1UEAwwOdWJpcmNoIFJvb3QgQ0ExDTALBgNVBC4TBGRlbW8wWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAARku8FNha1EV%2BEWJ%2Fe4o4vXk9yCqOAamd%2F2rKgNEFLX%2FSCcmcpnshe7j32ZkqlJRxb7%2BOugknVpGY%2FKMQdMo8jzo4IBWTCCAVUwHQYDVR0OBBYEFNUg3YrCATZXmbPrDvWjJksVronjMB8GA1UdIwQYMBaAFNUg3YrCATZXmbPrDvWjJksVronjMA8GA1UdEwEB%2FwQFMAMBAf8wDgYDVR0PAQH%2FBAQDAgEGMFkGA1UdHgRSMFCgTjARgg9kZW1vLnViaXJjaC5jb20wEoIQLmRlbW8udWJpcmNoLmNvbTARgQ9kZW1vLnViaXJjaC5jb20wEoEQLmRlbW8udWJpcmNoLmNvbTBQBggrBgEFBQcBAQREMEIwQAYIKwYBBQUHMAKGNGh0dHBzOi8vdHJ1c3QudWJpcmNoLmNvbS9kZW1vL2FpYS91YmlyY2hfUm9vdF9DQS5wZW0wRQYDVR0fBD4wPDA6oDigNoY0aHR0cHM6Ly90cnVzdC51YmlyY2guY29tL2RlbW8vY3JsL3ViaXJjaF9Sb290X0NBLnBlbTAKBggqhkjOPQQDAgNIADBFAiA95s2D5KTZh1SmnMMLafh5RaNByijrbtZ%2FgoL0fEmATgIhAL13n861igEymUpzJqJNfRRF5odFOeRtcNlelrxZ1qkF")

    logging(CertMaterializer.parse(cert))
    logging(CertMaterializer.parse(cert2))
    logging(CertMaterializer.parse(cert3))
    logging(CertMaterializer.parse(cert4))
    logging(CertMaterializer.parse(cert5))

    logging(CertMaterializer.parse(CertMaterializer.pemFromEncodedContent("MIIDRTCCAuqgAwIBAgIUM6%2B%2F%2BmJPd8SCJ6Pzvx5W4NrGdfYwCgYIKoZIzj0EAwIwbTELMAkGA1UEBhMCREUxEDAOBgNVBAcMB0NvbG9nbmUxFDASBgNVBAoMC3ViaXJjaCBHbWJIMScwJQYDVQQDDB51YmlyY2ggR3JvdXAgQWNjZXNzIElzc3VpbmcgQ0ExDTALBgNVBC4TBGRlbW8wHhcNMjEwNDE2MDcyMjU0WhcNMjMwNDE2MDcyMjU0WjByMQswCQYDVQQGEwJERTEQMA4GA1UEBwwHQ29sb2duZTEUMBIGA1UECgwLdWJpcmNoIEdtYkgxLDAqBgNVBAMMI3ViaXJjaCBHcm91cCBBY2Nlc3M6IGJtZy12YWMgKGRlbW8pMQ0wCwYDVQQuEwRkZW1vMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE5GQQ5DSV7y1CO%2F8oImrf9rcN1J4552fyybzcGdWBiLYWdOPBkXJBWQwZIYORX0oRQXZZCxKjDeo37%2BTCDEkTbqOCAWEwggFdMB0GA1UdDgQWBBQLNGawcgkL2AyDvndtoG9XYpt52DAfBgNVHSMEGDAWgBQS0eimV8TqnHmROdUuTEhPbAbewTAMBgNVHRMBAf8EAjAAMA4GA1UdDwEB%2FwQEAwIHgDATBgNVHSUEDDAKBggrBgEFBQcDAjBgBggrBgEFBQcBAQRUMFIwUAYIKwYBBQUHMAKGRGh0dHBzOi8vdHJ1c3QudWJpcmNoLmNvbS9kZW1vL2FpYS91YmlyY2hfR3JvdXBfQWNjZXNzX0lzc3VpbmdfQ0EucGVtMFUGA1UdHwROMEwwSqBIoEaGRGh0dHBzOi8vdHJ1c3QudWJpcmNoLmNvbS9kZW1vL2NybC91YmlyY2hfR3JvdXBfQWNjZXNzX0lzc3VpbmdfQ0EucGVtMC8GA1UdEQQoMCaBJGJtZy12YWNAZ3JvdXAtYWNjZXNzLmRlbW8udWJpcmNoLmNvbTAKBggqhkjOPQQDAgNJADBGAiEAzzPV4ukVZewiY3UlMW4E%2BQdeKQnLID1SXajFS5I2KwwCIQCBHm5up3NIStgRLTQo2FvYVEHfkC4yN9%2FWWBSPJdgmbw%3D%3D")))

    val serverCert =
      "MIIDjTCCAnWgAwIBAgIJAKM9YDT55MA8MA0GCSqGSIb3DQEBCwUAMFMxCzAJBgNV%0D%0ABAYTAkRFMQ8wDQYDVQQIDAZCZXJsaW4xDzANBgNVBAcMBkJlcmxpbjEUMBIGA1UE%0D%0ACgwLVWJpcmNoIEdtYkgxDDAKBgNVBAsMA0RldjAgFw0yMTA1MDcwODQxMDZaGA8y%0D%0AMTIxMDQxMzA4NDEwNlowGzEZMBcGA1UEAwwQd3d3LmhvZ2Vob2dlLmNvbTCCASIw%0D%0ADQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALq7EM%2Fe2%2FT9abl72myZC0HcziA%2B%0D%0Af0VEyZ%2BW3Gus3O0WwHP6k0TYBwOv5pp4gV25PrqxJ71NjgDboREP0WHJy%2Fdor4mh%0D%0A7GgOzgdpENvQDcCbdzingPPG88N9QbhMJqzN8rLvIDSJiYofYotTazRCSTSOiWRv%0D%0ANmh7XSi78V8OmL%2FZ51TQgClZy2MXbAIPiW4ItA7b7XI9qO9EqXNQBREEivEkGHJs%0D%0ABx2kHuqqijA7nmmaHlMsROJuHWWrkitrpOmghUZZ3YisrPHU%2F7RSGv9u2DwfVdyF%0D%0AJyAgl%2FnyFNIVHjh3xDxqjuMGu5a0i26wAM3qhMmlY9mtk411WwD9w7pis30CAwEA%0D%0AAaOBmTCBljAMBgNVHRMBAf8EAjAAMAsGA1UdDwQEAwIFoDAdBgNVHSUEFjAUBggr%0D%0ABgEFBQcDAQYIKwYBBQUHAwIwHQYDVR0OBBYEFCg%2F2ldMBGNxjb0kCEtoDt%2BjgcTt%0D%0AMB8GA1UdIwQYMBaAFG0QDoK2U%2FoMpC7YTCJEreC4GnulMBoGA1UdEQQTMBGCD3d3%0D%0Ady5leGFtcGxlLmNvbTANBgkqhkiG9w0BAQsFAAOCAQEAFpXTDD7MtTXEIXPhy5tZ%0D%0AxpaeOWTLnxpVgBHAABpSByI3ms3G7FIgxi8h5c3BsP%2F8HbMqugJUj4BN3YB%2B3%2Bkg%0D%0AT7BAewXZYcWCZl6oqNwWtljg24jjk6p6zdRNGWIW3ZWmtEA%2FDg4wkgv2ilNxO2hu%0D%0A9vxY4mHPafrvCJRbYOOgVf9pABpQrv4R6mIGQGuxAtYr%2Bw5%2FQRgBPvjB3apuu9Pa%0D%0AdbGv2JMhzGtaH%2F4n9b9elxVNnplpU%2BMM993I6Oj7LIB9ZNDDmTR7StlnlkoFvcBx%0D%0AJOnRETy0JvIh0%2BaqNoAwAiiqS%2Bb0GjzfiSZZ01t6eBwsjZ8c0emBBCkiHqGFz3Cq%0D%0AkA%3D%3D"
    val rootCert =
      "MIIDqjCCApKgAwIBAgIJAJY8LkbVdLZAMA0GCSqGSIb3DQEBCwUAMFMxCzAJBgNVBAYTAkRFMQ8wDQYDVQQIDAZCZXJsaW4xDzANBgNVBAcMBkJlcmxpbjEUMBIGA1UECgwLVWJpcmNoIEdtYkgxDDAKBgNVBAsMA0RldjAgFw0yMTA0MTMwODA1NTFaGA8yMTIxMDMyMDA4MDU1MVowUzELMAkGA1UEBhMCREUxDzANBgNVBAgMBkJlcmxpbjEPMA0GA1UEBwwGQmVybGluMRQwEgYDVQQKDAtVYmlyY2ggR21iSDEMMAoGA1UECwwDRGV2MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyILKUzAm2EFynZcEuVdSi8WOAKY9hpUld3lP%2FViZNz6i4w3p46F13HCagqvvYODVCDYUP8UjNjyb6mcxe3KOrIxKyEba%2BUJxhMb8wrRCmCbiQc4GA8VAoXLgGgnnXcuqL7LnCEzUMsLTwn%2FvllzHjOCUuy8%2BiyrpeM0bhj%2B98Gw%2BYtmfvbHaz8HSISws8Tyy6YZdJDFWNtFg4pZ%2Bf0q8PHjWttDRETyDbvlOwuAtFkdwDNkPbZKtqDs4LZ4FCBDmjSDEv9%2FQ5tfpmulPjUBo25eIMRB2scsFp4pleKzfSC%2F%2F%2FlQMqUyoZNvIywfVpL%2F8EFAqlf6DcfFixjK7BdCa2QIDAQABo38wfTAPBgNVHRMBAf8EBTADAQH%2FMAsGA1UdDwQEAwIBBjAdBgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwHQYDVR0OBBYEFG0QDoK2U%2FoMpC7YTCJEreC4GnulMB8GA1UdIwQYMBaAFG0QDoK2U%2FoMpC7YTCJEreC4GnulMA0GCSqGSIb3DQEBCwUAA4IBAQCQVRR6gxpk%2Fni%2FOtp0edPA914wPJcWzQWnOmWT13lbyC5ezA5Vl1nqr9%2BVK43VWWhGqvtED1PRXfLnE%2FH6fwlZ66eJMUzzilPl15lBai1NfafZM0c56%2B3SmCkybFZTOqFl%2FdFCT%2FCWXlwmAAeP%2Fhgdtvmt0VcwdkBETokfk%2BfRg4zyvmtmKWljravhCSAmsekgkFqjVbswE783Sxq4PNWGjOFBYk%2BuoJlv13bNPKjWyrPbJucWFfrKZ988yxahaGcHBncJqiEp4wMyzREJz5h8haWuGR53MEqBHuh9tjUosl5TGK8V4dGmQRTffe%2BWrjbSRcyuPdN6%2B5ewJnrvZc5J"
    logging(CertMaterializer.parse(CertMaterializer.pemFromEncodedContent(serverCert)))
    logging(CertMaterializer.parse(CertMaterializer.pemFromEncodedContent(rootCert)))
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
