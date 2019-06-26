package com.softwaremill.session

import java.nio.charset.StandardCharsets.UTF_8
import java.security.spec.PKCS8EncodedKeySpec
import java.security.{KeyFactory, PrivateKey, Signature}
import java.util.Base64

import com.typesafe.config.Config

import scala.util.{Failure, Success, Try}

sealed trait JwsAlgorithm {
  def sign(message: String): String
}

object JwsAlgorithm {

  case class Rsa(privateKey: PrivateKey) extends JwsAlgorithm {

    override def sign(message: String): String = {
      val privateSignature: Signature = Signature.getInstance("SHA256withRSA")
      privateSignature.initSign(privateKey)
      privateSignature.update(message.getBytes(UTF_8))

      Base64.getEncoder.encodeToString(privateSignature.sign())
    }
  }

  object Rsa {

    def fromConfig(jwsConfig: Config): Try[Rsa] = {

      def readKeyFromConf(): Try[String] = {
        val configKey = "rsa-private-key"
        Option(jwsConfig.hasPath(configKey))
          .filter(identity)
          .flatMap(_ => Option(jwsConfig.getString(configKey)))
          .filter(_.trim.nonEmpty)
          .map(_.replaceAll("\\s", "").replaceAll("-----[^-]+-----", ""))
          .map(Success(_))
          .getOrElse(Failure(new IllegalArgumentException(
            "akka.http.session.jws.rsa-private-key must be defined in order to use alg = RS256")))
      }

      readKeyFromConf()
        .flatMap { key =>
          Try {
            val keyFactory = KeyFactory.getInstance("RSA")
            val privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder.decode(key)))
            Rsa(privateKey)
          }.recoverWith {
            case ex => Failure(new IllegalArgumentException("Invalid RSA private key", ex))
          }
        }
    }
  }

  case class HmacSHA256(serverSecret: String) extends JwsAlgorithm {
    override def sign(message: String): String = Crypto.sign_HmacSHA256_base64(message, serverSecret)
  }

}
