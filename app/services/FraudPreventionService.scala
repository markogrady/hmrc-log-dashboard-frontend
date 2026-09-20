package services

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import config.AppConfig
import models.FraudHeaderCombination
import models.viewmodels._
import repositories.{ApplicationRepository, LogEntryRepository}

/** Checks the fraud prevention headers seen across ALL of an application's requests. */
@Singleton
class FraudPreventionService @Inject() (
  applicationRepository: ApplicationRepository,
  logEntryRepository: LogEntryRepository,
  appConfig: AppConfig
)(implicit ec: ExecutionContext) {

  import FraudPreventionService._

  /** None when the application does not exist. */
  def report(appId: UUID): Future[Option[FraudPreventionReport]] =
    applicationRepository.findById(appId).flatMap {
      case None      => Future.successful(None)
      case Some(app) =>
        logEntryRepository
          .fraudHeaderCombinations(appId)
          .map { combinations =>
            Some(
              buildReport(
                ApplicationOption(app.id, app.name, app.environment, app.softwareVersion),
                combinations,
                FraudCheckThresholds(appConfig.fraudMinDistinctClientValues, appConfig.fraudMinDistinctLicenceIds)
              )
            )
          }
    }
}

object FraudPreventionService {

  def buildReport(
    application: ApplicationOption,
    combinations: Seq[FraudHeaderCombination],
    thresholds: FraudCheckThresholds
  ): FraudPreventionReport =
    FraudPreventionReport(
      application = application,
      totalRequests = combinations.map(_.requests).sum,
      requestsWithoutHeaders = combinations.filterNot(_.hasAnyHeader).map(_.requests).sum,
      checks = Seq(
        vendorVsClientIp(combinations),
        diversity("clientPublicIps", combinations, thresholds.minDistinctClientValues, CheckStatus.Fail)(
          _.govClientPublicIp.toSeq.map(_.trim)
        ),
        diversity("deviceIds", combinations, thresholds.minDistinctClientValues, CheckStatus.Fail)(
          _.govClientDeviceId.toSeq.map(_.trim)
        ),
        diversity("localIps", combinations, thresholds.minDistinctClientValues, CheckStatus.Fail)(
          _.govClientLocalIps.toSeq.map(normaliseLocalIps)
        ),
        diversity("licenceIds", combinations, thresholds.minDistinctLicenceIds, CheckStatus.Warning)(
          _.govVendorLicenseIds.toSeq.flatMap(splitLicenceIds)
        )
      )
    )

  /**
   * The vendor (server) public IP must never be one of the client public IPs. Compared across all
   * requests, not just within one, so a server IP reused as a client IP on another request is caught too.
   */
  private def vendorVsClientIp(combinations: Seq[FraudHeaderCombination]): FraudCheckResult = {
    val withBoth  = combinations.filter(c => c.govClientPublicIp.isDefined && c.govVendorPublicIp.isDefined)
    val clientIps = combinations.flatMap(_.govClientPublicIp).map(_.trim).toSet
    val vendorIps = combinations.flatMap(_.govVendorPublicIp).map(_.trim).toSet
    val shared    = clientIps.intersect(vendorIps)

    val values    = shared.toSeq.map(ip => ip -> combinations.filter(usesIp(ip)).map(_.requests).sum)

    FraudCheckResult(
      key = "vendorVsClientIp",
      status =
        if (withBoth.isEmpty) CheckStatus.NoData
        else if (shared.nonEmpty) CheckStatus.Fail
        else CheckStatus.Pass,
      distinctCount = shared.size,
      required = None,
      values = sortValues(values),
      affectedRequests = combinations.filter(c => shared.exists(usesIp(_)(c))).map(_.requests).sum
    )
  }

  private def usesIp(ip: String)(c: FraudHeaderCombination): Boolean =
    c.govClientPublicIp.exists(_.trim == ip) || c.govVendorPublicIp.exists(_.trim == ip)

  /** Counts distinct values of one header; fewer than `required` gives `belowStatus`, none at all gives NoData. */
  private def diversity(key: String, combinations: Seq[FraudHeaderCombination], required: Int, belowStatus: CheckStatus)(
    extract: FraudHeaderCombination => Seq[String]
  ): FraudCheckResult = {
    val values = combinations
      .flatMap(c => extract(c).filter(_.nonEmpty).distinct.map(_ -> c.requests))
      .groupMapReduce(_._1)(_._2)(_ + _)
      .toSeq

    FraudCheckResult(
      key = key,
      status =
        if (values.isEmpty) CheckStatus.NoData
        else if (values.size < required) belowStatus
        else CheckStatus.Pass,
      distinctCount = values.size,
      required = Some(required),
      values = sortValues(values)
    )
  }

  /** Gov-Client-Local-IPs is a comma-separated list; the same addresses in a different order are the same device. */
  private def normaliseLocalIps(header: String): String =
    header.split(',').map(_.trim).filter(_.nonEmpty).distinct.sorted.mkString(", ")

  /** Gov-Vendor-License-IDs is `software=hash&other=hash`; each pair is one licence. */
  private def splitLicenceIds(header: String): Seq[String] =
    header.split('&').map(_.trim).filter(_.nonEmpty).toSeq

  private def sortValues(values: Seq[(String, Long)]): Seq[(String, Long)] =
    values.sortBy { case (value, requests) => (-requests, value) }
}
