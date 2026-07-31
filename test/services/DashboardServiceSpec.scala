package services

import java.time.Instant

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import models._

class DashboardServiceSpec extends AnyWordSpec with Matchers {

  private val t0 = Instant.parse("2026-06-10T09:00:00Z")

  private def line(
    correlationId: String,
    endpointKey: String = "ListBusinessesController.listBusinesses",
    apiArea: String = "Business Details",
    statusCode: Option[Int] = None,
    kind: LogEventKind = LogEventKind.Start,
    timestamp: Instant = t0
  ): LogEntry =
    LogEntry(
      rawLine = "raw",
      timestampUtc = timestamp,
      correlationId = Some(correlationId),
      endpointKey = Some(endpointKey),
      apiArea = Some(apiArea),
      kind = kind,
      statusCode = statusCode,
      message = "m",
      parsed = true
    )

  "worstStatus" should {

    "take the max status across a request's lines" in {
      DashboardService.worstStatus(
        Seq(line("c1"), line("c1", statusCode = Some(200)), line("c1", statusCode = Some(404)))
      ) shouldBe 404
    }

    "default to 500 when no status but a Failure-kind line exists" in {
      DashboardService.worstStatus(Seq(line("c1"), line("c1", kind = LogEventKind.Failure))) shouldBe 500
    }

    "default to 200 when no status and no failure" in {
      DashboardService.worstStatus(Seq(line("c1"), line("c1", kind = LogEventKind.Success))) shouldBe 200
    }
  }

  "endpointTag" should {

    "be Fix when any 5xx" in {
      DashboardService.endpointTag(total = 100, failures = 1, any5xx = true) shouldBe HealthTag.Fix
    }

    "be Fix when failure rate is above 25%" in {
      DashboardService.endpointTag(total = 100, failures = 26, any5xx = false) shouldBe HealthTag.Fix
    }

    "be Review at exactly 25% (boundary is strictly greater-than)" in {
      DashboardService.endpointTag(total = 100, failures = 25, any5xx = false) shouldBe HealthTag.Review
    }

    "be Review with any failure" in {
      DashboardService.endpointTag(total = 100, failures = 1, any5xx = false) shouldBe HealthTag.Review
    }

    "be Correct with no failures" in {
      DashboardService.endpointTag(total = 100, failures = 0, any5xx = false) shouldBe HealthTag.Correct
    }
  }

  "buildSummary" should {

    "group lines into requests by correlationId" in {
      val lines = Seq(
        line("c1"),
        line("c1", statusCode = Some(200), kind = LogEventKind.Success),
        line("c2"),
        line("c2", statusCode = Some(404), kind = LogEventKind.Failure)
      )

      val summary = DashboardService.buildSummary(lines, Some(t0))

      summary.totalRequests shouldBe 2
      val row = summary.areas.head.endpoints.head
      row.requests shouldBe 2
      row.failureCount shouldBe 1
      row.successCount shouldBe 1
      row.tag shouldBe HealthTag.Fix // 1 of 2 = 50% failure rate > 25%
    }

    "give the area the worst endpoint tag and sort areas worst-first then by volume" in {
      val lines = Seq(
        // healthy area, high volume
        line("a1", endpointKey = "ListEmploymentsController.listEmployments", apiArea = "Employments Income", statusCode = Some(200)),
        line("a2", endpointKey = "ListEmploymentsController.listEmployments", apiArea = "Employments Income", statusCode = Some(200)),
        line("a3", endpointKey = "ListEmploymentsController.listEmployments", apiArea = "Employments Income", statusCode = Some(200)),
        // unhealthy area, low volume: a 500
        line("b1", endpointKey = "TriggerBsasController.triggerBsas", apiArea = "Business Source Adjustable Summary", statusCode = Some(500))
      )

      val summary = DashboardService.buildSummary(lines, Some(t0))

      summary.areas.map(_.apiArea) shouldBe Seq("Business Source Adjustable Summary", "Employments Income")
      summary.areas.head.tag shouldBe HealthTag.Fix
      summary.areas(1).tag shouldBe HealthTag.Correct
    }

    "round success percent to 1dp and use 100 for zero requests" in {
      val lines = (1 to 3).map(i => line(s"c$i", statusCode = Some(200))) :+
        line("c4", statusCode = Some(404))

      val summary = DashboardService.buildSummary(lines, Some(t0))
      summary.areas.head.endpoints.head.successPercent shouldBe 75.0
    }
  }

  "buildEndpointDetail" should {

    "produce a breakdown grouped by (status, errorCode) with Fix/Review actions and percentages" in {
      val key   = "SubmitFinalDeclarationController.submitFinalDeclaration"
      val lines =
        (1 to 6).map(i => line(s"ok$i", endpointKey = key, statusCode = Some(200))) ++
          (1 to 3).map(i => line(s"nf$i", endpointKey = key, statusCode = Some(404)).copy(errorCode = Some("MATCHING_RESOURCE_NOT_FOUND"))) :+
          line("boom", endpointKey = key, statusCode = Some(500)).copy(errorCode = Some("INTERNAL_SERVER_ERROR"))

      val detail = DashboardService.buildEndpointDetail(key, lines)

      detail.totalRequests shouldBe 10
      detail.failureCount shouldBe 4
      detail.breakdown should have size 2
      val worst404 = detail.breakdown.find(_.details.contains("404")).get
      worst404.action shouldBe "Review"
      worst404.requests shouldBe 3
      worst404.percent shouldBe 30.0
      val worst500 = detail.breakdown.find(_.details.contains("500")).get
      worst500.action shouldBe "Fix"
      worst500.details shouldBe "HTTP 500 — INTERNAL_SERVER_ERROR"
    }

    "cap recent requests at 100, newest first" in {
      val key   = "ListBusinessesController.listBusinesses"
      val lines = (1 to 150).map { i =>
        line(s"c$i", endpointKey = key, statusCode = Some(200), timestamp = t0.plusSeconds(i))
      }

      val detail = DashboardService.buildEndpointDetail(key, lines)

      detail.recentRequests should have size 100
      detail.recentRequests.head.correlationId shouldBe "c150"
    }
  }
}
