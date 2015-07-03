package com.softwaremill.session

import com.typesafe.config.Config

object SessionUtil {
  implicit class PimpedConfig(config: Config) {
    def getStringOption(path: String): Option[String]   = if (config.hasPath(path)) Some(config.getString(path)) else None
    def getLongOption(path: String): Option[Long]       = if (config.hasPath(path)) Some(config.getLong(path)) else None
    def getBooleanOption(path: String): Option[Boolean] = if (config.hasPath(path)) Some(config.getBoolean(path)) else None
  }
}
