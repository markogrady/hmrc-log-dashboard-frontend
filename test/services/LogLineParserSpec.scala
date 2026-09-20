package services

import java.time.{LocalDateTime, ZoneOffset}
import java.util.UUID

import models._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class LogLineParserSpec extends AnyWordSpec with Matchers {

  private val parser = new LogLineParser
  private val appId  = "8dab5c01-ea7a-45c0-ae6a-c008edc1498e"

  "LogLineParser" should {

    "parse a start line with appId and clientId" in {
      val line =
        s"2026-06-14 09:15:23,123 level=[INFO] logger=[v3.controllers.RetrieveEmploymentController] " +
          s"thread=[application-pekko.actor.default-dispatcher-14] rid=[c58e6bac-93c9-4bfa-95e2-1e7c4a2b0f1d] " +
          s"appId=[$appId] clientId=[CUST-004221] " +
          s"message=[[RetrieveEmploymentController][retrieveEmployment] Retrieve an employment for NINO : AA123456A " +
          s"with correlationId : 4fbe4211-6bb5-4bc2-9d10-a2f761b2dc0e]"

      val entry = parser.parse(line)

      entry.parsed shouldBe true
      entry.timestampUtc shouldBe LocalDateTime.of(2026, 6, 14, 9, 15, 23, 123000000).toInstant(ZoneOffset.UTC)
      entry.level shouldBe LogSeverity.Info
      entry.logger shouldBe Some("v3.controllers.RetrieveEmploymentController")
      entry.requestId shouldBe Some("c58e6bac-93c9-4bfa-95e2-1e7c4a2b0f1d")
      entry.applicationId shouldBe Some(UUID.fromString(appId))
      entry.clientId shouldBe Some("CUST-004221")
      entry.controller shouldBe Some("RetrieveEmploymentController")
      entry.endpoint shouldBe Some("retrieveEmployment")
      entry.correlationId shouldBe Some("4fbe4211-6bb5-4bc2-9d10-a2f761b2dc0e")
      entry.identifier shouldBe Some("AA123456A")
      entry.regime shouldBe TaxRegime.Itsa
      entry.kind shouldBe LogEventKind.Start
      entry.apiArea shouldBe Some("Employments Income")
      entry.endpointKey shouldBe Some("RetrieveEmploymentController.retrieveEmployment")
      entry.httpMethod shouldBe Some("GET")
    }

    "parse a success line" in {
      val line =
        s"2026-06-14 09:15:24,001 level=[INFO] logger=[v3.controllers.RetrieveEmploymentController] " +
          s"thread=[application-pekko.actor.default-dispatcher-14] rid=[c58e6bac] appId=[$appId] clientId=[CUST-004221] " +
          s"message=[[RetrieveEmploymentController][retrieveEmployment] Success response received with correlationId : 4fbe4211]"

      val entry = parser.parse(line)

      entry.parsed shouldBe true
      entry.kind shouldBe LogEventKind.Success
      entry.correlationId shouldBe Some("4fbe4211")
    }

    "parse a 404 failure line with error code" in {
      val line =
        s"2026-06-14 09:20:11,555 level=[WARN] logger=[v3.controllers.RetrieveCalculationController] " +
          s"thread=[application-pekko.actor.default-dispatcher-3] rid=[aa11] appId=[$appId] clientId=[CUST-000007] " +
          "message=[[RetrieveCalculationController][retrieveCalculation] Error response received with status: 404 and body: " +
          "{\"code\":\"MATCHING_RESOURCE_NOT_FOUND\",\"message\":\"Matching resource not found\"} with correlationId : bb22]"

      val entry = parser.parse(line)

      entry.parsed shouldBe true
      entry.level shouldBe LogSeverity.Warn
      entry.kind shouldBe LogEventKind.Failure
      entry.statusCode shouldBe Some(404)
      entry.errorCode shouldBe Some("MATCHING_RESOURCE_NOT_FOUND")
      entry.apiArea shouldBe Some("Individual Calculations")
    }

    "parse a 500 failure line" in {
      val line =
        s"2026-06-14 11:02:09,010 level=[ERROR] logger=[v3.controllers.TriggerBsasController] thread=[t-1] rid=[r1] " +
          s"appId=[$appId] clientId=[CUST-000001] " +
          "message=[[TriggerBsasController][triggerBsas] Error response received with status: 500 and body: " +
          "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"An internal server error occurred\"} with correlationId : cc33]"

      val entry = parser.parse(line)

      entry.parsed shouldBe true
      entry.level shouldBe LogSeverity.Error
      entry.kind shouldBe LogEventKind.Failure
      entry.statusCode shouldBe Some(500)
      entry.errorCode shouldBe Some("INTERNAL_SERVER_ERROR")
      entry.apiArea shouldBe Some("Business Source Adjustable Summary")
    }

    "parse a plain vat-api line without appId or clientId" in {
      val line =
        "2026-06-01 08:00:00,000 level=[INFO] logger=[uk.gov.hmrc.vatapi.controllers.ObligationsController] " +
          "thread=[application-akka.actor.default-dispatcher-2] rid=[req-1] " +
          "message=[[ObligationsController][retrieveObligations] Retrieve obligations for VRN : 123456789 with correlationId : dd44]"

      val entry = parser.parse(line)

      entry.parsed shouldBe true
      entry.applicationId shouldBe None
      entry.clientId shouldBe None
      entry.identifier shouldBe Some("123456789")
      entry.regime shouldBe TaxRegime.Vat
      entry.apiArea shouldBe Some("VAT")
    }

    "parse the optional fraud prevention header fields" in {
      val line =
        s"2026-06-14 09:15:23,123 level=[INFO] logger=[v3.controllers.RetrieveEmploymentController] thread=[t-1] rid=[r1] " +
          s"appId=[$appId] clientId=[CUST-004221] " +
          "govClientPublicIP=[198.51.100.7] govVendorPublicIP=[203.0.113.6] " +
          "govClientDeviceID=[beec798b-b366-47fa-b1f8-92cede14a1ce] govClientLocalIPs=[10.1.2.3,10.3.4.2] " +
          "govVendorLicenseIDs=[my-software=8D7963490527D33716835EE7C195516D] " +
          "message=[[RetrieveEmploymentController][retrieveEmployment] Retrieve an employment for NINO : AA123456A " +
          "with correlationId : ff66]"

      val entry = parser.parse(line)

      entry.parsed shouldBe true
      entry.clientId shouldBe Some("CUST-004221")
      entry.govClientPublicIp shouldBe Some("198.51.100.7")
      entry.govVendorPublicIp shouldBe Some("203.0.113.6")
      entry.govClientDeviceId shouldBe Some("beec798b-b366-47fa-b1f8-92cede14a1ce")
      entry.govClientLocalIps shouldBe Some("10.1.2.3,10.3.4.2")
      entry.govVendorLicenseIds shouldBe Some("my-software=8D7963490527D33716835EE7C195516D")
      entry.correlationId shouldBe Some("ff66")
      entry.endpointKey shouldBe Some("RetrieveEmploymentController.retrieveEmployment")
    }

    "parse a line carrying only some fraud prevention header fields" in {
      val line =
        s"2026-06-14 09:15:23,123 level=[INFO] logger=[x] thread=[t] rid=[r] appId=[$appId] clientId=[C1] " +
          "govClientPublicIP=[198.51.100.7] govClientDeviceID=[] " +
          "message=[[RetrieveEmploymentController][retrieveEmployment] Retrieve with correlationId : gg77]"

      val entry = parser.parse(line)

      entry.parsed shouldBe true
      entry.govClientPublicIp shouldBe Some("198.51.100.7")
      entry.govVendorPublicIp shouldBe None
      entry.govClientDeviceId shouldBe None
      entry.govClientLocalIps shouldBe None
      entry.govVendorLicenseIds shouldBe None
    }

    "leave the fraud prevention header fields empty on lines without them" in {
      val line =
        s"2026-06-14 09:15:24,001 level=[INFO] logger=[x] thread=[t] rid=[r] appId=[$appId] clientId=[C1] " +
          "message=[[RetrieveEmploymentController][retrieveEmployment] Success response received with correlationId : hh88]"

      val entry = parser.parse(line)

      entry.parsed shouldBe true
      entry.govClientPublicIp shouldBe None
      entry.govVendorLicenseIds shouldBe None
    }

    "keep but flag an unrecognised controller" in {
      val line =
        s"2026-06-14 09:15:23,123 level=[INFO] logger=[x] thread=[t] rid=[r] appId=[$appId] clientId=[C1] " +
          "message=[[MysteryController][doMystery] Something for NINO : AB000001C with correlationId : ee55]"

      val entry = parser.parse(line)

      entry.parsed shouldBe true
      entry.apiArea shouldBe Some("Unrecognised")
      entry.endpointKey shouldBe Some("MysteryController.doMystery")
    }

    "return garbage lines unparsed, not thrown" in {
      val garbage = List(
        "this is not a log line at all",
        "2026-06-14 not quite a timestamp level=[INFO]",
        ""
      )

      garbage.foreach { line =>
        val entry = parser.parse(line)
        entry.parsed shouldBe false
        entry.rawLine shouldBe line
      }
    }

    "recognise catalog routes via the path fallback matcher" in {
      val matched = ItsaEndpointCatalog.tryMatchPath(
        "/individuals/calculations/AA123456A/self-assessment/2025-26/trigger/intent-to-finalise",
        Some("POST")
      )

      matched.map(_.key) shouldBe Some("TriggerCalculationController.triggerCalculation")
    }
  }
}
