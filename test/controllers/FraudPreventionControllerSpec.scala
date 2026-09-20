package controllers

import java.util.UUID

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers._

import controllers.actions.SessionKeys
import repositories.LogEntryRepository
import services.LogIngestionService

/** Needs a local mongod (like the repository integration specs) — the check reads ingested lines from Mongo. */
class FraudPreventionControllerSpec
    extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite
    with ScalaFutures
    with IntegrationPatience
    with BeforeAndAfterAll {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        "seed-on-startup"  -> false,
        "auditing.enabled" -> false,
        "metrics.enabled"  -> false,
        "mongodb.uri"      -> "mongodb://localhost:27017/hmrc-log-dashboard-frontend-fraud-prevention-spec"
      )
      .build()

  private val signedIn = SessionKeys.userId -> "dev@hmrclogger.local"
  private val appId    = UUID.fromString("8dab5c01-ea7a-45c0-ae6a-c008edc1498e")

  private def line(correlationId: String, clientIp: String, deviceId: String, localIp: String): String =
    s"2026-06-14 09:15:23,123 level=[INFO] logger=[v3.controllers.RetrieveEmploymentController] thread=[t-1] rid=[r-$correlationId] " +
      s"appId=[$appId] clientId=[CUST-004221] " +
      s"govClientPublicIP=[$clientIp] govVendorPublicIP=[203.0.113.10] govClientDeviceID=[$deviceId] " +
      s"govClientLocalIPs=[$localIp] govVendorLicenseIDs=[soft=AAAA] " +
      s"message=[[RetrieveEmploymentController][retrieveEmployment] Retrieve an employment for NINO : AA123456A " +
      s"with correlationId : $correlationId]"

  override def beforeAll(): Unit = {
    super.beforeAll()
    app.injector.instanceOf[LogEntryRepository].deleteAll().futureValue
    app.injector
      .instanceOf[LogIngestionService]
      .ingest(
        Seq(
          line("fp-1", "198.51.100.1", "device-1", "192.168.1.10"),
          line("fp-2", "198.51.100.2", "device-2", "192.168.2.20"),
          // server IP reused as a client IP
          line("fp-3", "203.0.113.10", "device-3", "10.0.0.4")
        )
      )
      .futureValue
  }

  "GET /dashboard/:appId/fraud-prevention-headers" should {

    "redirect to login when unauthenticated" in {
      val result = route(app, FakeRequest(GET, s"/dashboard/$appId/fraud-prevention-headers")).get
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get should startWith("/login")
    }

    "return the GDS 404 page for an unknown application" in {
      val request = FakeRequest(GET, s"/dashboard/${UUID.randomUUID()}/fraud-prevention-headers").withSession(signedIn)
      val result  = route(app, request).get

      status(result) shouldBe NOT_FOUND
      contentAsString(result) should include("Page not found")
    }

    "report the checks over all of the application's requests" in {
      val request = FakeRequest(GET, s"/dashboard/$appId/fraud-prevention-headers").withSession(signedIn)
      val result  = route(app, request).get

      status(result) shouldBe OK
      val html = contentAsString(result)
      html should include("Fraud prevention header checks")
      html should include("The server IP must be different from the client IP")
      // one licence across three requests => the licence warning
      html should include("Distinct Gov-Vendor-License-IDs found: 1")
      html should include("203.0.113.10")
      html should not include "All fraud prevention header checks passed"
    }
  }

  "GET /dashboard" should {
    "link to the fraud prevention header check for the selected application" in {
      val result = route(app, FakeRequest(GET, s"/dashboard?appId=$appId").withSession(signedIn)).get

      status(result) shouldBe OK
      contentAsString(result) should include(s"/dashboard/$appId/fraud-prevention-headers")
    }
  }
}
