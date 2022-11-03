/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.cipemailverification.services

import akka.stream.ConnectionException
import org.mockito.ArgumentMatchersSugar.any
import org.mockito.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.http.Status.{ACCEPTED, BAD_GATEWAY, BAD_REQUEST, CONFLICT, CREATED, FORBIDDEN, INTERNAL_SERVER_ERROR, OK, SERVICE_UNAVAILABLE, TOO_MANY_REQUESTS}
import play.api.libs.json.{Json, OWrites}
import play.api.test.Helpers.{contentAsJson, contentAsString, defaultAwaitTimeout, status}
import uk.gov.hmrc.cipemailverification.config.AppConfig
import uk.gov.hmrc.cipemailverification.connectors.{GovUkConnector, ValidateConnector}
import uk.gov.hmrc.cipemailverification.models.EmailPasscodeData
import uk.gov.hmrc.cipemailverification.models.api.Email
import uk.gov.hmrc.cipemailverification.models.api.ErrorResponse.Codes
import uk.gov.hmrc.cipemailverification.models.api.ErrorResponse.Message._
import uk.gov.hmrc.cipemailverification.models.domain.data.EmailAndPasscode
import uk.gov.hmrc.cipemailverification.models.http.govnotify.GovUkNotificationId
import uk.gov.hmrc.cipemailverification.models.http.validation.ValidatedEmail
import uk.gov.hmrc.cipemailverification.utils.DateTimeUtils
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class VerifyServiceSpec extends AnyWordSpec
  with Matchers
  with IdiomaticMockito {

  "verify" should {
    "return success if email address is valid" in new SetUp {
      val email = Email("test@test.com")
      val emailPasscodeDataFromDb = EmailPasscodeData(email.email, passcode, now)

      // return Ok from email validation
      validateConnectorMock.callService(email.email)
        .returns(Future.successful(HttpResponse(OK, """{"email": "test@test.com"}""")))

      passcodeServiceMock.persistPasscode(any[EmailPasscodeData])
        .returns(Future.successful(emailPasscodeDataFromDb))

      govUkConnectorMock.sendPasscode(emailPasscodeDataFromDb)
        .returns(Future.successful(Right(HttpResponse(CREATED, Json.toJson(GovUkNotificationId("test-notification-id")).toString()))))

      val result = verifyService.verifyEmail(email)

      status(result) shouldBe ACCEPTED
      contentAsString(result) shouldBe empty

      // check what is sent to validation service
      validateConnectorMock.callService("test@test.com")(any[HeaderCarrier]) was called
      passcodeGeneratorMock.passcodeGenerator() was called
      // check what is sent to the cache
      passcodeServiceMock.persistPasscode(emailPasscodeDataFromDb) was called
      // Check what is sent to GovNotify
      govUkConnectorMock.sendPasscode(emailPasscodeDataFromDb)(any[HeaderCarrier]) was called
    }

    "return bad request if email is invalid" in new SetUp {
      val enteredEmail = Email("test")
      validateConnectorMock.callService(enteredEmail.email)
        .returns(Future.successful(HttpResponse(BAD_REQUEST, """{"res": "res"}""")))

      val result = verifyService.verifyEmail(enteredEmail)

      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "res").as[String] shouldBe "res"
      passcodeGeneratorMock wasNever called
      passcodeServiceMock wasNever called
      govUkConnectorMock wasNever called
    }

    "return internal sever error when datastore exception occurs" in new SetUp {
      val enteredEmail = Email("test")
      val normalisedEmailAndPasscode = EmailAndPasscode("test", passcode)
      val emailPasscodeDataFromDb = EmailPasscodeData(normalisedEmailAndPasscode.email, normalisedEmailAndPasscode.passcode, now)
      validateConnectorMock.callService(enteredEmail.email)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedEmail(normalisedEmailAndPasscode.email)).toString())))
      passcodeServiceMock.persistPasscode(any[EmailPasscodeData])
        .returns(Future.failed(new Exception("simulated database operation failure")))

      val result = verifyService.verifyEmail(enteredEmail)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "code").as[Int] shouldBe Codes.PASSCODE_PERSISTING_FAIL.id
      (contentAsJson(result) \ "message").as[String] shouldBe SERVER_EXPERIENCED_AN_ISSUE
      // check what is sent to validation service
      validateConnectorMock.callService("test")(any[HeaderCarrier]) was called
      passcodeGeneratorMock.passcodeGenerator() was called
      // check what is sent to DAO
      passcodeServiceMock.persistPasscode(emailPasscodeDataFromDb) was called

      // Check NOTHING is sent to GovNotify
      govUkConnectorMock wasNever called
    }

    "return bad gateway when validation service returns 503" in new SetUp {
      val email = Email("test")
      validateConnectorMock.callService(email.email)
        .returns(Future.successful(HttpResponse(SERVICE_UNAVAILABLE, "")))

      val result = verifyService.verifyEmail(email)

      status(result) shouldBe BAD_GATEWAY
      (contentAsJson(result) \ "code").as[Int] shouldBe Codes.EXTERNAL_SERVER_ERROR.id
      (contentAsJson(result) \ "message").as[String] shouldBe EXTERNAL_SERVER_EXPERIENCED_AN_ISSUE
    }

    "return service unavailable when validation service throws connection exception" in new SetUp {
      val email = Email("test")
      validateConnectorMock.callService(email.email)
        .returns(Future.failed(new ConnectionException("")))

      val result = verifyService.verifyEmail(email)

      status(result) shouldBe SERVICE_UNAVAILABLE
      (contentAsJson(result) \ "code").as[Int] shouldBe Codes.SERVER_CURRENTLY_UNAVAILABLE.id
      (contentAsJson(result) \ "message").as[String] shouldBe SERVER_CURRENTLY_UNAVAILABLE
    }

    "return service unavailable when govUk notify service throws connection exception" in new SetUp {
      val enteredEmail = Email("test")
      val normalisedEmailAndPasscode = EmailAndPasscode("test", passcode)
      val emailPasscodeDataFromDb = EmailPasscodeData(normalisedEmailAndPasscode.email, normalisedEmailAndPasscode.passcode, now)
      validateConnectorMock.callService(enteredEmail.email)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedEmail(normalisedEmailAndPasscode.email)).toString())))
      passcodeServiceMock.persistPasscode(emailPasscodeDataFromDb)
        .returns(Future.successful(emailPasscodeDataFromDb))
      govUkConnectorMock.sendPasscode(any[EmailPasscodeData])
        .returns(Future.failed(new ConnectionException("")))

      val result = verifyService.verifyEmail(Email(enteredEmail.email))

      status(result) shouldBe SERVICE_UNAVAILABLE
      (contentAsJson(result) \ "code").as[Int] shouldBe Codes.EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE.id
      (contentAsJson(result) \ "message").as[String] shouldBe EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE
    }

    "return BadGateway if gov-notify returns internal server error" in new SetUp {
      val enteredEmail = Email("test")
      val normalisedEmailAndPasscode = EmailAndPasscode("test", passcode)
      val emailPasscodeDataFromDb = EmailPasscodeData(normalisedEmailAndPasscode.email, normalisedEmailAndPasscode.passcode, now)
      validateConnectorMock.callService(enteredEmail.email)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedEmail(normalisedEmailAndPasscode.email)).toString())))
      passcodeServiceMock.persistPasscode(any[EmailPasscodeData])
        .returns(Future.successful(emailPasscodeDataFromDb))
      govUkConnectorMock.sendPasscode(any[EmailPasscodeData])
        .returns(Future.successful(Left(UpstreamErrorResponse("Server currently unavailable", INTERNAL_SERVER_ERROR))))

      val result = verifyService.verifyEmail(enteredEmail)

      status(result) shouldBe BAD_GATEWAY
      (contentAsJson(result) \ "code").as[Int] shouldBe Codes.EXTERNAL_SERVER_ERROR.id
      (contentAsJson(result) \ "message").as[String] shouldBe EXTERNAL_SERVER_EXPERIENCED_AN_ISSUE
      validateConnectorMock.callService("test")(any[HeaderCarrier]) was called
      passcodeGeneratorMock.passcodeGenerator() was called
      passcodeServiceMock.persistPasscode(emailPasscodeDataFromDb) was called
      govUkConnectorMock.sendPasscode(emailPasscodeDataFromDb)(any[HeaderCarrier]) was called
    }

    "return Service unavailable if gov-notify returns BadRequestError" in new SetUp {
      val enteredEmail = Email("test")
      val normalisedEmailAndPasscode = EmailAndPasscode("test", passcode)
      val emailPasscodeDataFromDb = EmailPasscodeData(normalisedEmailAndPasscode.email, normalisedEmailAndPasscode.passcode, now)
      validateConnectorMock.callService(enteredEmail.email)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedEmail(normalisedEmailAndPasscode.email)).toString())))
      passcodeServiceMock.persistPasscode(any[EmailPasscodeData])
        .returns(Future.successful(emailPasscodeDataFromDb))
      govUkConnectorMock.sendPasscode(any[EmailPasscodeData])
        .returns(Future.successful(Left(UpstreamErrorResponse("External server currently unavailable", BAD_REQUEST))))

      val result = verifyService.verifyEmail(enteredEmail)

      status(result) shouldBe SERVICE_UNAVAILABLE
      (contentAsJson(result) \ "code").as[Int] shouldBe Codes.EXTERNAL_SERVER_FAIL_VALIDATION.id
      (contentAsJson(result) \ "message").as[String] shouldBe SERVER_EXPERIENCED_AN_ISSUE
      validateConnectorMock.callService("test")(any[HeaderCarrier]) was called
      passcodeGeneratorMock.passcodeGenerator() was called
      passcodeServiceMock.persistPasscode(emailPasscodeDataFromDb) was called
      govUkConnectorMock.sendPasscode(emailPasscodeDataFromDb)(any[HeaderCarrier]) was called
    }

    "return Service unavailable if gov-notify returns Forbidden error" in new SetUp {
      val enteredEmail = Email("test")
      val normalisedEmailAndPasscode = EmailAndPasscode("test", passcode)
      val emailPasscodeDataFromDb = EmailPasscodeData(normalisedEmailAndPasscode.email, normalisedEmailAndPasscode.passcode, now)
      validateConnectorMock.callService(enteredEmail.email)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedEmail(normalisedEmailAndPasscode.email)).toString())))
      passcodeServiceMock.persistPasscode(any[EmailPasscodeData])
        .returns(Future.successful(emailPasscodeDataFromDb))
      govUkConnectorMock.sendPasscode(any[EmailPasscodeData])
        .returns(Future.successful(Left(UpstreamErrorResponse("External server currently unavailable", FORBIDDEN))))

      val result = verifyService.verifyEmail(enteredEmail)

      status(result) shouldBe SERVICE_UNAVAILABLE
      (contentAsJson(result) \ "code").as[Int] shouldBe Codes.EXTERNAL_SERVER_FAIL_FORBIDDEN.id
      (contentAsJson(result) \ "message").as[String] shouldBe SERVER_EXPERIENCED_AN_ISSUE
      validateConnectorMock.callService("test")(any[HeaderCarrier]) was called
      passcodeGeneratorMock.passcodeGenerator() was called
      passcodeServiceMock.persistPasscode(emailPasscodeDataFromDb) was called
      govUkConnectorMock.sendPasscode(emailPasscodeDataFromDb)(any[HeaderCarrier]) was called
    }

    "return Too Many Requests if gov-notify returns RateLimitError or TooManyRequestsError" in new SetUp {
      val enteredEmail = Email("test")
      val normalisedEmailAndPasscode = EmailAndPasscode("test", passcode)
      val emailPasscodeDataFromDb = EmailPasscodeData(normalisedEmailAndPasscode.email, normalisedEmailAndPasscode.passcode, now)
      validateConnectorMock.callService(enteredEmail.email)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedEmail(normalisedEmailAndPasscode.email)).toString())))
      passcodeServiceMock.persistPasscode(any[EmailPasscodeData])
        .returns(Future.successful(emailPasscodeDataFromDb))
      govUkConnectorMock.sendPasscode(any[EmailPasscodeData])
        .returns(Future.successful(Left(UpstreamErrorResponse("External server currently unavailable", TOO_MANY_REQUESTS))))

      val result = verifyService.verifyEmail(enteredEmail)

      status(result) shouldBe TOO_MANY_REQUESTS
      (contentAsJson(result) \ "code").as[Int] shouldBe Codes.MESSAGE_THROTTLED_OUT.id
      (contentAsJson(result) \ "message").as[String] shouldBe "The request for the API is throttled as you have exceeded your quota"
      validateConnectorMock.callService("test")(any[HeaderCarrier]) was called
      passcodeGeneratorMock.passcodeGenerator() was called
      passcodeServiceMock.persistPasscode(emailPasscodeDataFromDb) was called
      govUkConnectorMock.sendPasscode(emailPasscodeDataFromDb)(any[HeaderCarrier]) was called
    }

    "return response from service if response has not been handled" in new SetUp {
      val enteredEmail = Email("test")
      val normalisedEmailAndPasscode = EmailAndPasscode("test", passcode)
      val emailPasscodeDataFromDb = EmailPasscodeData(normalisedEmailAndPasscode.email, normalisedEmailAndPasscode.passcode, now)
      validateConnectorMock.callService(enteredEmail.email)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedEmail(normalisedEmailAndPasscode.email)).toString())))
      passcodeServiceMock.persistPasscode(any[EmailPasscodeData])
        .returns(Future.successful(emailPasscodeDataFromDb))
      govUkConnectorMock.sendPasscode(any[EmailPasscodeData])
        .returns(Future.successful(Left(UpstreamErrorResponse("Some random message from external service", CONFLICT))))

      val result = verifyService.verifyEmail(enteredEmail)

      status(result) shouldBe CONFLICT
      contentAsString(result) shouldBe empty
      validateConnectorMock.callService("test")(any[HeaderCarrier]) was called
      passcodeGeneratorMock.passcodeGenerator() was called
      passcodeServiceMock.persistPasscode(emailPasscodeDataFromDb) was called
      govUkConnectorMock.sendPasscode(emailPasscodeDataFromDb)(any[HeaderCarrier]) was called
    }

  }

  trait SetUp {
    implicit val hc: HeaderCarrier = new HeaderCarrier()
    implicit val govUkNotificationIdWrites: OWrites[GovUkNotificationId] = Json.writes[GovUkNotificationId]
    val passcodeServiceMock: PasscodeService = mock[PasscodeService]
    val validateConnectorMock: ValidateConnector = mock[ValidateConnector]
    val govUkConnectorMock: GovUkConnector = mock[GovUkConnector]
    val passcodeGeneratorMock: PasscodeGenerator = mock[PasscodeGenerator]
    val dateTimeUtilsMock: DateTimeUtils = mock[DateTimeUtils]
    val passcode = "ABCDEF"
    passcodeGeneratorMock.passcodeGenerator().returns(passcode)
    val now = System.currentTimeMillis()
    dateTimeUtilsMock.getCurrentDateTime().returns(now)

    private val appConfig = new AppConfig(
      Configuration.from(Map(
        "passcode.expiry" -> 15,
        "cache.expiry" -> 120
      ))
    )

    val verifyService = new VerifyService(
      passcodeGeneratorMock,
      passcodeServiceMock,
      dateTimeUtilsMock,
      govUkConnectorMock,
      validateConnectorMock,
      appConfig)
  }
}


