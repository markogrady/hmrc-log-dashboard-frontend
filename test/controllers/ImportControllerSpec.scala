package controllers

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers._

import controllers.actions.SessionKeys

/** Needs a local mongod (like the repository integration specs) — ingestion writes to Mongo. */
class ImportControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        "seed-on-startup"  -> false,
        "auditing.enabled" -> false,
        "metrics.enabled"  -> false,
        "mongodb.uri"      -> "mongodb://localhost:27017/hmrc-log-dashboard-frontend-import-spec"
      )
      .build()

  private val signedIn = SessionKeys.userId -> "dev@hmrclogger.local"

  "GET /dashboard/import" should {
    "redirect to login when unauthenticated" in {
      val result = route(app, FakeRequest(GET, "/dashboard/import")).get
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get should startWith("/login")
    }
  }

  "POST /dashboard/import/paste" should {

    "reject an empty paste with a GDS error" in {
      val request = FakeRequest(POST, "/dashboard/import/paste")
        .withSession(signedIn)
        .withFormUrlEncodedBody("log-paste" -> "   ")
        .withCSRFToken
      val result  = route(app, request).get

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include("There is a problem")
      contentAsString(result) should include("Enter at least one log line")
    }

    "ingest pasted lines and report counts including unparsed samples" in {
      val lines   = scala.io.Source.fromResource("sample.log").mkString
      val request = FakeRequest(POST, "/dashboard/import/paste")
        .withSession(signedIn)
        .withFormUrlEncodedBody("log-paste" -> lines)
        .withCSRFToken
      val result  = route(app, request).get

      status(result) shouldBe OK
      val html = contentAsString(result)
      html should include("Import complete")
      html should include("4 lines read, 3 parsed, 1 could not be parsed")
      html should include("this line is complete garbage")
    }
  }
}
