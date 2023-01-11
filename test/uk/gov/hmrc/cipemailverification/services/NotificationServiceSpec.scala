/*
 * Copyright 2023 HM Revenue & Customs
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
import play.api.http.Status
import play.api.libs.json.Format.GenericFormat
import play.api.libs.json._
import play.api.test.Helpers._
import uk.gov.hmrc.cipemailverification.connectors.GovUkConnector
import uk.gov.hmrc.cipemailverification.models.domain.audit.AuditType.EmailVerificationDeliveryResultRequest
import uk.gov.hmrc.cipemailverification.models.domain.audit.VerificationDeliveryResultRequestAuditEvent
import uk.gov.hmrc.cipemailverification.models.domain.result._
import uk.gov.hmrc.cipemailverification.models.http.govnotify.GovUkNotificationStatusResponse
import uk.gov.hmrc.cipemailverification.utils.GovNotifyUtils
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source

class NotificationServiceSpec extends AnyWordSpec
  with Matchers
  with IdiomaticMockito {

  "status" should {
    "return Created for created status" in new SetUp {
      private val govUkNotificationStatus = buildGovNotifyResponseWithStatus("created")
      private val httpResponse = HttpResponse(Status.OK, Json.toJson(govUkNotificationStatus), Map.empty[String, Seq[String]])
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(httpResponse))

      private val result = await(service.status(notificationId))

      result shouldBe Right(Created)

      mockGovNotifyUtils.extractPasscodeFromGovNotifyBody(expectedGovNotifyResponseBody) was called
      private val expectedAuditEvent = VerificationDeliveryResultRequestAuditEvent(expectedEmail, passcode, notificationId, "created")
      mockAuditService.sendExplicitAuditEvent(EmailVerificationDeliveryResultRequest, expectedAuditEvent) was called
    }

    "return Sending for sending status" in new SetUp {
      private val govUkNotificationStatus = buildGovNotifyResponseWithStatus("sending")
      private val httpResponse = HttpResponse(Status.OK, Json.toJson(govUkNotificationStatus), Map.empty[String, Seq[String]])
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(httpResponse))

      private val result = await(service.status(notificationId))

      result shouldBe Right(Sending)

      mockGovNotifyUtils.extractPasscodeFromGovNotifyBody(expectedGovNotifyResponseBody) was called
      private val expectedAuditEvent = VerificationDeliveryResultRequestAuditEvent(expectedEmail, passcode, notificationId, "sending")
      mockAuditService.sendExplicitAuditEvent(EmailVerificationDeliveryResultRequest, expectedAuditEvent) was called
    }

    "return Pending for pending status" in new SetUp {
      private val govUkNotificationStatus = buildGovNotifyResponseWithStatus("pending")
      private val httpResponse = HttpResponse(Status.OK, Json.toJson(govUkNotificationStatus), Map.empty[String, Seq[String]])
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(httpResponse))

      private val result = await(service.status(notificationId))

      result shouldBe Right(Pending)

      mockGovNotifyUtils.extractPasscodeFromGovNotifyBody(expectedGovNotifyResponseBody) was called
      private val expectedAuditEvent = VerificationDeliveryResultRequestAuditEvent(expectedEmail, passcode, notificationId, "pending")
      mockAuditService.sendExplicitAuditEvent(EmailVerificationDeliveryResultRequest, expectedAuditEvent) was called
    }

    "return Sent for sent (international number) status" in new SetUp {
      private val govUkNotificationStatus = buildGovNotifyResponseWithStatus("sent")
      private val httpResponse = HttpResponse(Status.OK, Json.toJson(govUkNotificationStatus), Map.empty[String, Seq[String]])
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(httpResponse))

      private val result = await(service.status(notificationId))

      result shouldBe Right(Sent)

      mockGovNotifyUtils.extractPasscodeFromGovNotifyBody(expectedGovNotifyResponseBody) was called
      private val expectedAuditEvent = VerificationDeliveryResultRequestAuditEvent(expectedEmail, passcode, notificationId, "sent")
      mockAuditService.sendExplicitAuditEvent(EmailVerificationDeliveryResultRequest, expectedAuditEvent) was called
    }

    "return Delivered for delivered status" in new SetUp {
      private val govUkNotificationStatus = buildGovNotifyResponseWithStatus("delivered")
      private val httpResponse = HttpResponse(Status.OK, Json.toJson(govUkNotificationStatus), Map.empty[String, Seq[String]])
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(httpResponse))

      private val result = await(service.status(notificationId))

      result shouldBe Right(Delivered)
    }

    "return PermanentFailure for permanent-failure status" in new SetUp {
      private val govUkNotificationStatus = buildGovNotifyResponseWithStatus("permanent-failure")
      private val httpResponse = HttpResponse(Status.OK, Json.toJson(govUkNotificationStatus), Map.empty[String, Seq[String]])
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(httpResponse))

      private val result = await(service.status(notificationId))

      result shouldBe Right(PermanentFailure)

      mockGovNotifyUtils.extractPasscodeFromGovNotifyBody(expectedGovNotifyResponseBody) was called
      private val expectedAuditEvent = VerificationDeliveryResultRequestAuditEvent(expectedEmail, passcode,
        notificationId, "permanent-failure")
      mockAuditService.sendExplicitAuditEvent(EmailVerificationDeliveryResultRequest, expectedAuditEvent) was called
    }

    "return TemporaryFailure for temporary-failure status" in new SetUp {
      private val govUkNotificationStatus = buildGovNotifyResponseWithStatus("temporary-failure")
      private val httpResponse = HttpResponse(Status.OK, Json.toJson(govUkNotificationStatus), Map.empty[String, Seq[String]])
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(httpResponse))

      private val result = await(service.status(notificationId))

      result shouldBe Right(TemporaryFailure)

      mockGovNotifyUtils.extractPasscodeFromGovNotifyBody(expectedGovNotifyResponseBody) was called
      private val expectedAuditEvent = VerificationDeliveryResultRequestAuditEvent(expectedEmail, passcode,
        notificationId, "temporary-failure")
      mockAuditService.sendExplicitAuditEvent(EmailVerificationDeliveryResultRequest, expectedAuditEvent) was called
    }

    "return TechnicalFailure for technical-failure status" in new SetUp {
      private val govUkNotificationStatus = buildGovNotifyResponseWithStatus("technical-failure")
      private val httpResponse = HttpResponse(Status.OK, Json.toJson(govUkNotificationStatus),
        Map.empty[String, Seq[String]])
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(httpResponse))

      private val result = await(service.status(notificationId))

      result shouldBe Right(TechnicalFailure)

      mockGovNotifyUtils.extractPasscodeFromGovNotifyBody(expectedGovNotifyResponseBody) was called
      private val expectedAuditEvent = VerificationDeliveryResultRequestAuditEvent(expectedEmail, passcode,
        notificationId, "technical-failure")
      mockAuditService.sendExplicitAuditEvent(EmailVerificationDeliveryResultRequest, expectedAuditEvent) was called
    }

    "return NotFound when notification id not found" in new SetUp {
      private val httpResponse = HttpResponse(Status.NOT_FOUND, "")
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(httpResponse))

      private val result = await(service.status(notificationId))

      result shouldBe Left(NotFound)

      mockGovNotifyUtils wasNever called
      private val expectedAuditEvent = VerificationDeliveryResultRequestAuditEvent("No_data_found", "No_data_found",
        notificationId, "No_data_found")
      mockAuditService.sendExplicitAuditEvent(EmailVerificationDeliveryResultRequest, expectedAuditEvent) was called
    }

    "return ErrorResponse when notification id is not valid uuid" in new SetUp {
      private val httpResponse = HttpResponse(Status.BAD_REQUEST, "")
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(httpResponse))

      private val result = await(service.status(notificationId))

      result shouldBe Left(ValidationError)
    }

    "return ErrorResponse when gov uk notify returns 403" in new SetUp {
      private val httpResponse = HttpResponse(Status.FORBIDDEN, "")
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(httpResponse))

      private val result = await(service.status(notificationId))

      result shouldBe Left(GovNotifyForbidden)
    }

    "return Gateway timeout if gov-notify connection times-out" in new SetUp {
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.failed(new ConnectionException("")))

      private val result = await(service.status(notificationId))

      result shouldBe Left(GovNotifyServiceDown)
    }
  }

  trait SetUp {
    implicit val writes: OWrites[GovUkNotificationStatusResponse] = Json.writes[GovUkNotificationStatusResponse]
    implicit val hc: HeaderCarrier = HeaderCarrier()
    protected val mockAuditService: AuditService = mock[AuditService]
    protected val mockGovUkConnector: GovUkConnector = mock[GovUkConnector]
    protected val mockGovNotifyUtils: GovNotifyUtils = mock[GovNotifyUtils]
    protected val service = new NotificationService(mockGovNotifyUtils, mockAuditService, mockGovUkConnector)
    protected val notificationId = "test"
    protected val passcode = "ABCDEF"
    protected val expectedEmail = "test@test.com"
    protected val expectedGovNotifyResponseBody: String =
      """CIP Email Verification Service: theTaxService needs to verify your email.
        |Your email verification code is ABCDEF.
        |Use this code within 10 minutes to verify your email.""".stripMargin
    mockGovNotifyUtils.extractPasscodeFromGovNotifyBody(any[String]).returns(passcode)
  }

  private def buildGovNotifyResponseWithStatus(status: String): JsValue = {
    val source: String =
      Source.fromFile("test/uk/gov/hmrc/cipemailverification/data/govNotifyGetMessageStatusResponse.json")
        .getLines().mkString
    val json: JsValue = Json.parse(source)

    val jsonTransformerForStatusAndRest = (__).json.update(
      __.read[JsObject].map { o => o ++ Json.obj("status" -> JsString(status)) }
    )

    val updatedJsonWithUpdatedStatusAndRest = json.transform(jsonTransformerForStatusAndRest) match {
      case JsSuccess(x, _) => x
      case JsError(_) => fail()
    }

    updatedJsonWithUpdatedStatusAndRest
  }
}
