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

import org.mockito.ArgumentMatchersSugar.any
import org.mockito.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.http.Status.{ACCEPTED, BAD_REQUEST, CREATED, OK}
import play.api.libs.json.{Json, OWrites}
import play.api.test.Helpers.{contentAsJson, contentAsString, defaultAwaitTimeout, status}
import uk.gov.hmrc.cipemailverification.connectors.{GovUkConnector, ValidateConnector}
import uk.gov.hmrc.cipemailverification.models.EmailPasscodeData
import uk.gov.hmrc.cipemailverification.models.api.Email
import uk.gov.hmrc.cipemailverification.models.http.govnotify.GovUkNotificationId
import uk.gov.hmrc.cipemailverification.utils.DateTimeUtils
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

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
      // check what is sent to DAO
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

    val verifyService = new VerifyService(
      passcodeGeneratorMock,
      passcodeServiceMock,
      dateTimeUtilsMock,
      govUkConnectorMock,
      validateConnectorMock)

  }
}


