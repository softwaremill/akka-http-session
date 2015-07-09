package com.softwaremill.session

import org.scalacheck.{Gen, Prop, Properties}

object SessionManagerTest extends Properties("SessionManager")  {

  import Prop._

  val secretGen = Gen.choose(64, 256).flatMap(size => Gen.listOfN(size, Gen.alphaNumChar).map(_.mkString))

  property("encode+decode") = forAllNoShrink(secretGen) { (secret: String) =>
    forAll { (encrypt: Boolean, useMaxAgeSeconds: Boolean, data: Map[String, String]) =>
      val config = SessionConfig.default(secret)
        .withClientSessionEncryptData(encrypt)
        .withClientSessionMaxAgeSeconds(if (useMaxAgeSeconds) Some(3600L) else None)
      val manager = new SessionManager[Map[String, String]](config)

      manager.decodeClientSession(manager.encodeClientSession(data)).contains(data)
    }
  }

  property("doesn't decode expired session") = forAllNoShrink(secretGen) { (secret: String) =>
    forAll { (encrypt: Boolean, data: Map[String, String]) =>
      val config = SessionConfig.default(secret)
        .withClientSessionEncryptData(encrypt)
        .withClientSessionMaxAgeSeconds(Some(20L)) // expires after 20s
      val managerPast = new SessionManager[Map[String, String]](config) {
        override def nowMillis = 8172L * 1000L
      }
      val managerFuture = new SessionManager[Map[String, String]](config) {
        override def nowMillis = (8172L + 600L) * 1000L // 600s later
      }

      managerFuture.decodeClientSession(managerPast.encodeClientSession(data)).isEmpty
    }
  }
}
