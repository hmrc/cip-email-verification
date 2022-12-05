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
import play.api.http.Status._
import play.api.libs.json.{Json, OWrites}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.cipemailverification.config.AppConfig
import uk.gov.hmrc.cipemailverification.connectors.{GovUkConnector, ValidateConnector}
import uk.gov.hmrc.cipemailverification.metrics.MetricsService
import uk.gov.hmrc.cipemailverification.models.api.{Email, EmailAndPasscode}
import uk.gov.hmrc.cipemailverification.models.domain.audit.AuditType.{EmailVerificationCheck, EmailVerificationRequest}
import uk.gov.hmrc.cipemailverification.models.domain.audit.{VerificationCheckAuditEvent, VerificationRequestAuditEvent}
import uk.gov.hmrc.cipemailverification.models.domain.data.EmailAndPasscodeData
import uk.gov.hmrc.cipemailverification.models.domain.result._
import uk.gov.hmrc.cipemailverification.models.http.govnotify.GovUkNotificationId
import uk.gov.hmrc.cipemailverification.models.http.validation.ValidatedEmail
import uk.gov.hmrc.cipemailverification.utils.DateTimeUtils
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import java.time.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class VerifyServiceSpec extends AnyWordSpec
  with Matchers
  with IdiomaticMockito {

  "verify" should {
    "return PasscodeSent if email address is valid" in new SetUp {
      private val email = Email("test")
      private val emailPasscodeDataFromDb = EmailAndPasscodeData(email.email, passcode, now)

      // return Ok from email validation
      validateConnectorMock.callService(email.email)
        .returns(Future.successful(HttpResponse(OK, """{"email": "test"}""")))

      passcodeServiceMock.persistPasscode(any[EmailAndPasscodeData])
        .returns(Future.successful(emailPasscodeDataFromDb))

      govUkConnectorMock.sendPasscode(emailPasscodeDataFromDb)
        .returns(Future.successful(HttpResponse(CREATED, Json.toJson(GovUkNotificationId("test-notification-id")).toString())))

      private val result = await(verifyService.verifyEmail(email))

      result shouldBe Right(PasscodeSent(GovUkNotificationId("test-notification-id")))

      // check what is sent to validation service
      validateConnectorMock.callService("test")(any[HeaderCarrier]) was called
      passcodeGeneratorMock.passcodeGenerator() was called
      // check what is sent to the audit service
      private val expectedAuditEvent = VerificationRequestAuditEvent("test", passcode)
      auditServiceMock.sendExplicitAuditEvent(EmailVerificationRequest,
        expectedAuditEvent) was called
      // check what is sent to the cache
      passcodeServiceMock.persistPasscode(emailPasscodeDataFromDb) was called
      // Check what is sent to GovNotify
      govUkConnectorMock.sendPasscode(emailPasscodeDataFromDb)(any[HeaderCarrier]) was called
    }

    "return ValidationError if email is invalid" in new SetUp {
      private val email = Email("test")
      validateConnectorMock.callService(email.email)
        .returns(Future.successful(HttpResponse(BAD_REQUEST, """{"res": "res"}""")))

      private val result = await(verifyService.verifyEmail(email))

      result shouldBe Left(ValidationError)
      passcodeGeneratorMock wasNever called
      auditServiceMock wasNever called
      passcodeServiceMock wasNever called
      govUkConnectorMock wasNever called
    }

    "return DatabaseServiceDown when datastore exception occurs" in new SetUp {
      private val email = Email("test")
      private val normalisedEmailAndPasscode = EmailAndPasscode("test", passcode)
      private val emailPasscodeDataFromDb = EmailAndPasscodeData(normalisedEmailAndPasscode.email, normalisedEmailAndPasscode.passcode, now)
      validateConnectorMock.callService(email.email)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedEmail(normalisedEmailAndPasscode.email)).toString())))
      passcodeServiceMock.persistPasscode(any[EmailAndPasscodeData])
        .returns(Future.failed(new Exception("simulated database operation failure")))

      private val result = await(verifyService.verifyEmail(email))

      result shouldBe Left(DatabaseServiceDown)
      //      result shouldBe Left(DatabaseServiceDown(PASSCODE_PERSISTING_FAIL, SERVER_EXPERIENCED_AN_ISSUE))
      // check what is sent to validation service
      validateConnectorMock.callService("test")(any[HeaderCarrier]) was called
      passcodeGeneratorMock.passcodeGenerator() was called
      // check what is sent to the audit service
      private val expectedAuditEvent = VerificationRequestAuditEvent("test", passcode)
      auditServiceMock.sendExplicitAuditEvent(EmailVerificationRequest,
        expectedAuditEvent) was called
      // check what is sent to DAO
      passcodeServiceMock.persistPasscode(emailPasscodeDataFromDb) was called

      // Check NOTHING is sent to GovNotify
      govUkConnectorMock wasNever called
    }

    "return ValidationServiceError when validation service returns 503" in new SetUp {
      private val email = Email("test")
      validateConnectorMock.callService(email.email)
        .returns(Future.successful(HttpResponse(SERVICE_UNAVAILABLE, "")))

      private val result = await(verifyService.verifyEmail(email))

      result shouldBe Left(ValidationServiceError)

      validateConnectorMock.callService("test")(any[HeaderCarrier]) was called
      passcodeGeneratorMock wasNever called
      auditServiceMock wasNever called
      passcodeServiceMock wasNever called
      govUkConnectorMock wasNever called
    }

    "return ValidationServiceDown when validation service throws connection exception" in new SetUp {
      private val email = Email("test")
      validateConnectorMock.callService(email.email)
        .returns(Future.failed(new ConnectionException("")))

      private val result = await(verifyService.verifyEmail(email))

      result shouldBe Left(ValidationServiceDown)

      validateConnectorMock.callService("test")(any[HeaderCarrier]) was called
      passcodeGeneratorMock wasNever called
      auditServiceMock wasNever called
      passcodeServiceMock wasNever called
      govUkConnectorMock wasNever called
    }

    "return GovNotifyServiceDown when govUk notify service throws connection exception" in new SetUp {
      private val email = Email("test")
      private val normalisedEmailAndPasscode = EmailAndPasscode("test", passcode)
      private val emailPasscodeDataFromDb = EmailAndPasscodeData(normalisedEmailAndPasscode.email, normalisedEmailAndPasscode.passcode, now)
      validateConnectorMock.callService(email.email)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedEmail(normalisedEmailAndPasscode.email)).toString())))
      passcodeServiceMock.persistPasscode(emailPasscodeDataFromDb)
        .returns(Future.successful(emailPasscodeDataFromDb))
      govUkConnectorMock.sendPasscode(any[EmailAndPasscodeData])
        .returns(Future.failed(new ConnectionException("")))

      private val result = await(verifyService.verifyEmail(email))

      result shouldBe Left(GovNotifyServiceDown)

      validateConnectorMock.callService("test")(any[HeaderCarrier]) was called
      passcodeGeneratorMock.passcodeGenerator() was called
      // check what is sent to the audit service
      private val expectedAuditEvent = VerificationRequestAuditEvent("test", passcode)
      auditServiceMock.sendExplicitAuditEvent(EmailVerificationRequest,
        expectedAuditEvent) was called
      passcodeServiceMock.persistPasscode(emailPasscodeDataFromDb) was called
      govUkConnectorMock.sendPasscode(emailPasscodeDataFromDb)(any[HeaderCarrier]) was called
    }

    "return GovNotifyServerError if gov-notify returns internal server error" in new SetUp {
      private val email = Email("test")
      private val normalisedEmailAndPasscode = EmailAndPasscode("test", passcode)
      private val emailPasscodeDataFromDb = EmailAndPasscodeData(normalisedEmailAndPasscode.email, normalisedEmailAndPasscode.passcode, now)
      validateConnectorMock.callService(email.email)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedEmail(normalisedEmailAndPasscode.email)).toString())))
      passcodeServiceMock.persistPasscode(any[EmailAndPasscodeData])
        .returns(Future.successful(emailPasscodeDataFromDb))
      govUkConnectorMock.sendPasscode(any[EmailAndPasscodeData])
        .returns(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, "")))

      private val result = await(verifyService.verifyEmail(email))

      result shouldBe Left(GovNotifyServerError)
      validateConnectorMock.callService("test")(any[HeaderCarrier]) was called
      passcodeGeneratorMock.passcodeGenerator() was called
      // check what is sent to the audit service
      private val expectedAuditEvent = VerificationRequestAuditEvent("test", passcode)
      auditServiceMock.sendExplicitAuditEvent(EmailVerificationRequest,
        expectedAuditEvent) was called
      passcodeServiceMock.persistPasscode(emailPasscodeDataFromDb) was called
      govUkConnectorMock.sendPasscode(emailPasscodeDataFromDb)(any[HeaderCarrier]) was called
    }

    "return GovNotifyBadRequest if gov-notify returns BadRequestError" in new SetUp {
      private val email = Email("test")
      private val normalisedEmailAndPasscode = EmailAndPasscode("test", passcode)
      private val emailPasscodeDataFromDb = EmailAndPasscodeData(normalisedEmailAndPasscode.email, normalisedEmailAndPasscode.passcode, now)
      validateConnectorMock.callService(email.email)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedEmail(normalisedEmailAndPasscode.email)).toString())))
      passcodeServiceMock.persistPasscode(any[EmailAndPasscodeData])
        .returns(Future.successful(emailPasscodeDataFromDb))
      govUkConnectorMock.sendPasscode(any[EmailAndPasscodeData])
        .returns(Future.successful(HttpResponse(BAD_REQUEST, "")))

      private val result = await(verifyService.verifyEmail(email))

      result shouldBe Left(GovNotifyBadRequest)
      validateConnectorMock.callService("test")(any[HeaderCarrier]) was called
      passcodeGeneratorMock.passcodeGenerator() was called
      // check what is sent to the audit service
      private val expectedAuditEvent = VerificationRequestAuditEvent("test", passcode)
      auditServiceMock.sendExplicitAuditEvent(EmailVerificationRequest,
        expectedAuditEvent) was called
      passcodeServiceMock.persistPasscode(emailPasscodeDataFromDb) was called
      govUkConnectorMock.sendPasscode(emailPasscodeDataFromDb)(any[HeaderCarrier]) was called
    }

    "return GovNotifyForbidden if gov-notify returns Forbidden error" in new SetUp {
      private val email = Email("test")
      private val normalisedEmailAndPasscode = EmailAndPasscode("test", passcode)
      private val emailPasscodeDataFromDb = EmailAndPasscodeData(normalisedEmailAndPasscode.email, normalisedEmailAndPasscode.passcode, now)
      validateConnectorMock.callService(email.email)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedEmail(normalisedEmailAndPasscode.email)).toString())))
      passcodeServiceMock.persistPasscode(any[EmailAndPasscodeData])
        .returns(Future.successful(emailPasscodeDataFromDb))
      govUkConnectorMock.sendPasscode(any[EmailAndPasscodeData])
        .returns(Future.successful(HttpResponse(FORBIDDEN, "")))

      private val result = await(verifyService.verifyEmail(email))

      result shouldBe Left(GovNotifyForbidden)
      validateConnectorMock.callService("test")(any[HeaderCarrier]) was called
      passcodeGeneratorMock.passcodeGenerator() was called
      // check what is sent to the audit service
      private val expectedAuditEvent = VerificationRequestAuditEvent("test", passcode)
      auditServiceMock.sendExplicitAuditEvent(EmailVerificationRequest,
        expectedAuditEvent) was called
      passcodeServiceMock.persistPasscode(emailPasscodeDataFromDb) was called
      govUkConnectorMock.sendPasscode(emailPasscodeDataFromDb)(any[HeaderCarrier]) was called
    }

    "return GovNotifyTooManyRequests if gov-notify returns RateLimitError or TooManyRequestsError" in new SetUp {
      private val email = Email("test")
      private val normalisedEmailAndPasscode = EmailAndPasscode("test", passcode)
      private val emailPasscodeDataFromDb = EmailAndPasscodeData(normalisedEmailAndPasscode.email, normalisedEmailAndPasscode.passcode, now)
      validateConnectorMock.callService(email.email)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedEmail(normalisedEmailAndPasscode.email)).toString())))
      passcodeServiceMock.persistPasscode(any[EmailAndPasscodeData])
        .returns(Future.successful(emailPasscodeDataFromDb))
      govUkConnectorMock.sendPasscode(any[EmailAndPasscodeData])
        .returns(Future.successful(HttpResponse(TOO_MANY_REQUESTS, "")))

      private val result = await(verifyService.verifyEmail(email))

      result shouldBe Left(GovNotifyTooManyRequests)
      validateConnectorMock.callService("test")(any[HeaderCarrier]) was called
      passcodeGeneratorMock.passcodeGenerator() was called
      // check what is sent to the audit service
      private val expectedAuditEvent = VerificationRequestAuditEvent("test", passcode)
      auditServiceMock.sendExplicitAuditEvent(EmailVerificationRequest,
        expectedAuditEvent) was called
      passcodeServiceMock.persistPasscode(emailPasscodeDataFromDb) was called
      govUkConnectorMock.sendPasscode(emailPasscodeDataFromDb)(any[HeaderCarrier]) was called
    }
  }

  "verifyPasscode" should {
    "return PasscodeExpired if passcode has expired" in new SetUp {
      private val emailAndPasscode = EmailAndPasscode("enteredEmail", "enteredPasscode")
      // assuming the passcode expiry config is set to 15 minutes
      private val seventeenMinutes = Duration.ofMinutes(17).toMillis
      private val passcodeExpiryWillHaveElapsed = now - seventeenMinutes
      private val emailPasscodeDataFromDb =
        EmailAndPasscodeData(emailAndPasscode.email, emailAndPasscode.passcode, passcodeExpiryWillHaveElapsed)
      validateConnectorMock.callService(emailAndPasscode.email)
        .returns(Future.successful(HttpResponse(OK, """{"email": "enteredEmail"}""")))
      passcodeServiceMock.retrievePasscode(emailAndPasscode.email)
        .returns(Future.successful(Some(emailPasscodeDataFromDb)))

      private val result = await(verifyService.verifyPasscode(emailAndPasscode))

      result shouldBe Right(PasscodeExpired)
      // check what is sent to the audit service
      private val expectedAuditEvent = VerificationCheckAuditEvent("enteredEmail", "enteredPasscode", "Not verified", Some("Passcode expired"))
      auditServiceMock.sendExplicitAuditEvent(EmailVerificationCheck, expectedAuditEvent) was called
    }

    "return Verified if passcode matches" in new SetUp {
      private val emailAndPasscode = EmailAndPasscode("enteredEmail", "enteredPasscode")
      private val emailPasscodeDataFromDb =
        EmailAndPasscodeData(emailAndPasscode.email, emailAndPasscode.passcode, now)
      validateConnectorMock.callService(emailAndPasscode.email)
        .returns(Future.successful(HttpResponse(OK, """{"email": "enteredEmail"}""")))
      passcodeServiceMock.retrievePasscode(emailAndPasscode.email)
        .returns(Future.successful(Some(emailPasscodeDataFromDb)))

      private val result = await(verifyService.verifyPasscode(emailAndPasscode))

      result shouldBe Right(Verified)
      // check what is sent to the audit service
      private val expectedAuditEvent = VerificationCheckAuditEvent("enteredEmail", "enteredPasscode", "Verified")
      auditServiceMock.sendExplicitAuditEvent(EmailVerificationCheck, expectedAuditEvent) was called
    }

    "return NotFound if cache has expired or if passcode does not exist" in new SetUp {
      private val emailAndPasscode = EmailAndPasscode("enteredEmail", "enteredPasscode")
      validateConnectorMock.callService(emailAndPasscode.email)
        .returns(Future.successful(HttpResponse(OK, """{"email": "enteredEmail"}""")))
      passcodeServiceMock.retrievePasscode(emailAndPasscode.email)
        .returns(Future.successful(None))

      private val result = await(verifyService.verifyPasscode(emailAndPasscode))

      result shouldBe Left(NotFound)
      private val expectedAuditEvent = VerificationCheckAuditEvent("enteredEmail", "enteredPasscode", "Not verified", Some("Passcode does not exist"))
      auditServiceMock.sendExplicitAuditEvent(EmailVerificationCheck,
        expectedAuditEvent) was called
    }

    "return NotVerified if passcode does not match" in new SetUp {
      private val emailAndPasscode = EmailAndPasscode("enteredEmail", "enteredPasscode")
      private val emailPasscodeDataFromDb = EmailAndPasscodeData(emailAndPasscode.email, "passcodethatdoesnotmatch", now)
      validateConnectorMock.callService(emailAndPasscode.email)
        .returns(Future.successful(HttpResponse(OK, """{"email": "enteredEmail"}""")))
      passcodeServiceMock.retrievePasscode(emailAndPasscode.email)
        .returns(Future.successful(Some(emailPasscodeDataFromDb)))

      private val result = await(verifyService.verifyPasscode(emailAndPasscode))

      result shouldBe Right(NotVerified)
      private val expectedAuditEvent = VerificationCheckAuditEvent("enteredEmail", "enteredPasscode", "Not verified", Some("Passcode mismatch"))
      auditServiceMock.sendExplicitAuditEvent(EmailVerificationCheck,
        expectedAuditEvent) was called
    }

    "return ValidationError if email is invalid" in new SetUp {
      private val emailAndPasscode = EmailAndPasscode("enteredEmail", "enteredPasscode")
      validateConnectorMock.callService(emailAndPasscode.email)
        .returns(Future.successful(HttpResponse(BAD_REQUEST, """{"res": "res"}""")))

      private val result = await(verifyService.verifyPasscode(emailAndPasscode))

      result shouldBe Left(ValidationError)
      auditServiceMock wasNever called
    }

    "return DatabaseServiceDown when datastore exception occurs on get" in new SetUp {
      private val emailAndPasscode = EmailAndPasscode("enteredEmail", "enteredPasscode")
      validateConnectorMock.callService(emailAndPasscode.email)
        .returns(Future.successful(HttpResponse(OK, """{"email": "enteredEmail"}""")))
      passcodeServiceMock.retrievePasscode(emailAndPasscode.email)
        .returns(Future.failed(new Exception("simulated database operation failure")))

      private val result = await(verifyService.verifyPasscode(emailAndPasscode))

      result shouldBe Left(DatabaseServiceDown)
      auditServiceMock wasNever called
    }

    "return ValidationServiceError when validation service returns 503" in new SetUp {
      private val emailAndPasscode = EmailAndPasscode("enteredEmail", "enteredPasscode")
      validateConnectorMock.callService(emailAndPasscode.email)
        .returns(Future.successful(HttpResponse(SERVICE_UNAVAILABLE, "")))

      private val result = await(verifyService.verifyPasscode(emailAndPasscode))

      result shouldBe Left(ValidationServiceError)
    }

    "return ValidationServiceDown when validation service throws connection exception" in new SetUp {
      private val emailAndPasscode = EmailAndPasscode("enteredEmail", "enteredPasscode")
      validateConnectorMock.callService(emailAndPasscode.email)
        .returns(Future.failed(new ConnectionException("")))

      private val result = await(verifyService.verifyPasscode(emailAndPasscode))

      result shouldBe Left(ValidationServiceDown)
    }
  }

  trait SetUp {
    implicit val hc: HeaderCarrier = new HeaderCarrier()
    implicit val govUkNotificationIdWrites: OWrites[GovUkNotificationId] = Json.writes[GovUkNotificationId]
    protected val passcodeServiceMock: PasscodeService = mock[PasscodeService]
    protected val validateConnectorMock: ValidateConnector = mock[ValidateConnector]
    protected val govUkConnectorMock: GovUkConnector = mock[GovUkConnector]
    protected val auditServiceMock: AuditService = mock[AuditService]
    protected val passcodeGeneratorMock: PasscodeGenerator = mock[PasscodeGenerator]
    protected val mockMetricsService: MetricsService = mock[MetricsService]
    private val dateTimeUtilsMock: DateTimeUtils = mock[DateTimeUtils]
    protected val passcode = "ABCDEF"
    passcodeGeneratorMock.passcodeGenerator().returns(passcode)
    protected val now: Long = System.currentTimeMillis()
    dateTimeUtilsMock.getCurrentDateTime().returns(now)

    private val appConfig = new AppConfig(
      Configuration.from(Map(
        "passcode.expiry" -> 15,
        "cache.expiry" -> 120
      ))
    )

    protected val verifyService = new VerifyService(
      passcodeGeneratorMock,
      auditServiceMock,
      passcodeServiceMock,
      dateTimeUtilsMock,
      mockMetricsService,
      govUkConnectorMock,
      validateConnectorMock,
      appConfig)
  }
}
