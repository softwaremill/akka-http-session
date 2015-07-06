package com.softwaremill.session

import java.math.BigInteger
import java.util.concurrent.ThreadLocalRandom

import com.typesafe.config.Config

object SessionUtil {
  implicit class PimpedConfig(config: Config) {
    def getStringOption(path: String): Option[String]   = if (config.hasPath(path)) Some(config.getString(path)) else None
    def getLongOption(path: String): Option[Long]       = if (config.hasPath(path)) Some(config.getLong(path)) else None
    def getBooleanOption(path: String): Option[Boolean] = if (config.hasPath(path)) Some(config.getBoolean(path)) else None
  }

  def randomString(length: Int) = {
    // http://stackoverflow.com/questions/41107/how-to-generate-a-random-alpha-numeric-string
    val random = ThreadLocalRandom.current()
    new BigInteger(length * 5, random).toString(32) // because 2^5 = 32
  }

  def randomServerSecret() = randomString(128)
}
