package services

import java.util.UUID

import models.FraudHeaderCombination
import models.viewmodels._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class FraudPreventionServiceSpec extends AnyWordSpec with Matchers {

  private val application =
    ApplicationOption(UUID.fromString("8dab5c01-ea7a-45c0-ae6a-c008edc1498e"), "Tax Optimiser", "Production", "v1")
  private val thresholds  = FraudCheckThresholds(minDistinctClientValues = 2, minDistinctLicenceIds = 3)

  private def combination(
    clientIp: String = "198.51.100.1",
    vendorIp: String = "203.0.113.10",
    deviceId: String = "device-1",
    localIps: String = "192.168.1.10",
    licenceIds: String = "soft=AAAA",
    requests: Long = 1
  ): FraudHeaderCombination =
    FraudHeaderCombination(Some(clientIp), Some(vendorIp), Some(deviceId), Some(localIps), Some(licenceIds), requests)

  private def report(combinations: FraudHeaderCombination*): FraudPreventionReport =
    FraudPreventionService.buildReport(application, combinations, thresholds)

  private def check(result: FraudPreventionReport, key: String): FraudCheckResult =
    result.checks.find(_.key == key).get

  private val healthy = Seq(
    combination("198.51.100.1", deviceId = "device-1", localIps = "192.168.1.10", licenceIds = "soft=AAAA", requests = 5),
    combination("198.51.100.2", deviceId = "device-2", localIps = "192.168.2.20", licenceIds = "soft=BBBB", requests = 3),
    combination("198.51.100.3", deviceId = "device-3", localIps = "10.0.0.4", licenceIds = "soft=CCCC", requests = 2)
  )

  "FraudPreventionService.buildReport" should {

    "pass every check for varied clients, devices, networks and licences" in {
      val result = report(healthy *)

      result.totalRequests shouldBe 10
      result.requestsWithoutHeaders shouldBe 0
      result.checks.map(_.key) shouldBe Seq("vendorVsClientIp", "clientPublicIps", "deviceIds", "localIps", "licenceIds")
      result.checks.map(_.status).distinct shouldBe Seq(CheckStatus.Pass)
      result.overall shouldBe CheckStatus.Pass
      result.problems shouldBe empty
    }

    "list distinct values most used first with their request counts" in {
      check(report(healthy *), "clientPublicIps").values shouldBe
        Seq("198.51.100.1" -> 5L, "198.51.100.2" -> 3L, "198.51.100.3" -> 2L)
    }

    "fail when a request's server IP is the same as its client IP" in {
      val result = report(healthy :+ combination(clientIp = "203.0.113.10", requests = 4) *)
      val ips    = check(result, "vendorVsClientIp")

      ips.status shouldBe CheckStatus.Fail
      ips.values.map(_._1) shouldBe Seq("203.0.113.10")
      // every request uses 203.0.113.10 as its vendor IP, so all 14 are affected
      ips.affectedRequests shouldBe 14
      result.overall shouldBe CheckStatus.Fail
    }

    "fail when a server IP turns up as the client IP of a different request" in {
      val result = report(
        combination(clientIp = "198.51.100.1", vendorIp = "203.0.113.10"),
        combination(clientIp = "203.0.113.10", vendorIp = "203.0.113.11", deviceId = "device-2")
      )

      check(result, "vendorVsClientIp").status shouldBe CheckStatus.Fail
      check(result, "vendorVsClientIp").distinctCount shouldBe 1
    }

    "fail the diversity checks when everything comes from one device on one network" in {
      val result = report(combination(requests = 50))

      check(result, "vendorVsClientIp").status shouldBe CheckStatus.Pass
      check(result, "clientPublicIps").status shouldBe CheckStatus.Fail
      check(result, "deviceIds").status shouldBe CheckStatus.Fail
      check(result, "localIps").status shouldBe CheckStatus.Fail
      check(result, "clientPublicIps").distinctCount shouldBe 1
    }

    "treat the same local IPs in a different order as one value" in {
      val result = report(
        combination(localIps = "10.1.2.3,192.168.1.10"),
        combination(clientIp = "198.51.100.2", deviceId = "device-2", localIps = "192.168.1.10, 10.1.2.3")
      )

      check(result, "localIps").distinctCount shouldBe 1
      check(result, "localIps").status shouldBe CheckStatus.Fail
    }

    "warn, not fail, when there are fewer than three distinct licence IDs" in {
      val twoLicences = healthy.map(c => if (c.govVendorLicenseIds.contains("soft=CCCC")) c.copy(govVendorLicenseIds = Some("soft=AAAA")) else c)
      val result      = report(twoLicences *)

      check(result, "licenceIds").status shouldBe CheckStatus.Warning
      check(result, "licenceIds").distinctCount shouldBe 2
      result.overall shouldBe CheckStatus.Warning
      result.problems.map(_.key) shouldBe Seq("licenceIds")
    }

    "count each licence in a multi-licence header separately" in {
      val result = report(combination(licenceIds = "soft=AAAA&addon=BBBB"), combination(licenceIds = "soft=CCCC&addon=BBBB"))

      check(result, "licenceIds").distinctCount shouldBe 3
      check(result, "licenceIds").status shouldBe CheckStatus.Pass
    }

    "report no data when no request carries any header" in {
      val result = report(FraudHeaderCombination(requests = 7))

      result.totalRequests shouldBe 7
      result.requestsWithoutHeaders shouldBe 7
      result.checks.map(_.status).distinct shouldBe Seq(CheckStatus.NoData)
      result.overall shouldBe CheckStatus.NoData
    }

    "report no data for an application with no requests" in {
      val result = report()

      result.totalRequests shouldBe 0
      result.overall shouldBe CheckStatus.NoData
    }
  }
}
