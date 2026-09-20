package models.viewmodels

/** Outcome of one fraud prevention check. Order matters: Pass < NoData < Warning < Fail, so `max` picks the worst. */
enum CheckStatus {
  case Pass, NoData, Warning, Fail
}

object CheckStatus {
  given Ordering[CheckStatus] = Ordering.by(_.ordinal)
}

/** Minimum distinct values each check needs before it stops failing / warning. */
final case class FraudCheckThresholds(minDistinctClientValues: Int, minDistinctLicenceIds: Int)

/**
 * @param key            message-key suffix: vendorVsClientIp, clientPublicIps, deviceIds, localIps, licenceIds
 * @param distinctCount  distinct values seen (for vendorVsClientIp: IPs used as both vendor and client IP)
 * @param required       minimum distinct values to pass; None where the check is not a count
 * @param values         distinct values with the number of requests carrying each, most used first
 */
final case class FraudCheckResult(
  key: String,
  status: CheckStatus,
  distinctCount: Int,
  required: Option[Int],
  values: Seq[(String, Long)],
  affectedRequests: Long = 0
)

final case class FraudPreventionReport(
  application: ApplicationOption,
  totalRequests: Long,
  requestsWithoutHeaders: Long,
  checks: Seq[FraudCheckResult]
) {
  def overall: CheckStatus = if (checks.isEmpty) CheckStatus.NoData else checks.map(_.status).max

  def problems: Seq[FraudCheckResult] =
    checks.filter(c => c.status == CheckStatus.Fail || c.status == CheckStatus.Warning)
}
