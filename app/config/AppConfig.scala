package config

import javax.inject.{Inject, Singleton}
import play.api.Configuration

@Singleton
class AppConfig @Inject() (config: Configuration) {
  val appName: String          = config.get[String]("appName")
  val loginUsername: String    = config.get[String]("login.username")
  val loginPassword: String    = config.get[String]("login.password")
  val seedOnStartup: Boolean   = config.get[Boolean]("seed-on-startup")
  val uploadMaxSizeBytes: Long = config.get[Long]("upload.max-size-bytes")
}
