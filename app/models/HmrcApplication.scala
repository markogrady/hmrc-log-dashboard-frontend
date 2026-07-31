package models

import java.util.UUID

import play.api.libs.functional.syntax._
import play.api.libs.json._

/** A Developer Hub application whose id appears in appId=[…] MDC fields. */
final case class HmrcApplication(
  id: UUID,
  name: String,
  environment: String = "Production",
  softwareVersion: String = ""
)

object HmrcApplication {

  /** Mongo format: the application id (from Developer Hub) is the document _id, as a canonical UUID string. */
  val mongoFormat: OFormat[HmrcApplication] = (
    (__ \ "_id").format[UUID] and
      (__ \ "name").format[String] and
      (__ \ "environment").format[String] and
      (__ \ "softwareVersion").format[String]
  )(HmrcApplication.apply, a => (a.id, a.name, a.environment, a.softwareVersion))
}
