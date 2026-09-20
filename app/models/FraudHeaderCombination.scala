package models

/** One distinct combination of fraud prevention header values and how many requests carried it. */
final case class FraudHeaderCombination(
  govClientPublicIp: Option[String] = None,
  govVendorPublicIp: Option[String] = None,
  govClientDeviceId: Option[String] = None,
  govClientLocalIps: Option[String] = None,
  govVendorLicenseIds: Option[String] = None,
  requests: Long
) {
  def hasAnyHeader: Boolean =
    Seq(govClientPublicIp, govVendorPublicIp, govClientDeviceId, govClientLocalIps, govVendorLicenseIds).exists(_.isDefined)
}
