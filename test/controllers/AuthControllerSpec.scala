package controllers

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.SessionCookieBaker
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.crypto.Crypted
import uk.gov.hmrc.play.bootstrap.frontend.filters.crypto.SessionCookieCrypto

import controllers.actions.SessionKeys

class AuthControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        "seed-on-startup"    -> false,
        "auditing.enabled"   -> false,
        "metrics.enabled"    -> false,
        "mongodb.uri"        -> "mongodb://localhost:27017/hmrc-log-dashboard-frontend-auth-spec"
      )
      .build()

  "GET /dashboard (unauthenticated)" should {
    "redirect to the login page with a continue url" in {
      val result = route(app, FakeRequest(GET, "/dashboard")).get

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get should startWith("/login")
      redirectLocation(result).get should include("continue=%2Fdashboard")
    }
  }

  "GET /login" should {
    "render the sign-in form" in {
      val result = route(app, FakeRequest(GET, "/login")).get

      status(result) shouldBe OK
      contentAsString(result) should include("Sign in")
      contentAsString(result) should include("id=\"username\"")
    }
  }

  "POST /login" should {

    "show a GDS error summary anchored to the username field on bad credentials" in {
      val request = FakeRequest(POST, "/login")
        .withFormUrlEncodedBody("username" -> "wrong@example.com", "password" -> "nope")
        .withCSRFToken
      val result  = route(app, request).get

      status(result) shouldBe UNAUTHORIZED
      contentAsString(result) should include("There is a problem")
      contentAsString(result) should include("href=\"#username\"")
      session(result).get(SessionKeys.userId) shouldBe None
    }

    "show field errors when the form is empty" in {
      val request = FakeRequest(POST, "/login")
        .withFormUrlEncodedBody("username" -> "", "password" -> "")
        .withCSRFToken
      val result  = route(app, request).get

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include("Enter your email address")
      contentAsString(result) should include("Enter your password")
    }

    "set the session and follow the continue url on good credentials" in {
      val request = FakeRequest(POST, "/login?continue=%2Fdashboard%2Fimport")
        .withFormUrlEncodedBody("username" -> "dev@hmrclogger.local", "password" -> "LetMeIn-2026!")
        .withCSRFToken
      val result  = route(app, request).get

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/dashboard/import")

      // bootstrap encrypts the session cookie (mdtp); decrypt with the app's own
      // crypto + baker to assert the session carries the signed-in user.
      val mdtpCookie = cookies(result).get("mdtp")
      mdtpCookie should not be None

      val crypto    = app.injector.instanceOf[SessionCookieCrypto].crypto
      val baker     = app.injector.instanceOf[SessionCookieBaker]
      val decrypted = crypto.decrypt(Crypted(mdtpCookie.get.value)).value
      val decoded   = baker.decodeFromCookie(Some(mdtpCookie.get.copy(value = decrypted)))

      decoded.get(SessionKeys.userId) shouldBe Some("dev@hmrclogger.local")
    }

    "ignore non-local continue urls" in {
      // absolute, scheme-relative, and backslash-normalisation variants must all fall back to /dashboard
      val evil = Seq(
        "https%3A%2F%2Fevil.example.com", // https://evil.example.com
        "%2F%2Fevil.example.com",         // //evil.example.com
        "%2F%5Cevil.example.com",         // /\evil.example.com
        "%2F%2F%5Cevil.example.com"       // //\evil.example.com
      )
      evil.foreach { continue =>
        val request = FakeRequest(POST, s"/login?continue=$continue")
          .withFormUrlEncodedBody("username" -> "dev@hmrclogger.local", "password" -> "LetMeIn-2026!")
          .withCSRFToken
        val result  = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/dashboard")
      }
    }
  }

  "GET /sign-out" should {
    "clear the session and redirect home" in {
      val request = FakeRequest(GET, "/sign-out").withSession(SessionKeys.userId -> "dev@hmrclogger.local")
      val result  = route(app, request).get

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/")
      session(result).get(SessionKeys.userId) shouldBe None
    }
  }

  "GET /dashboard/import (authenticated)" should {
    "render the import page" in {
      val request = FakeRequest(GET, "/dashboard/import").withSession(SessionKeys.userId -> "dev@hmrclogger.local")
      val result  = route(app, request).get

      status(result) shouldBe OK
      contentAsString(result) should include("Import logs")
    }
  }
}
