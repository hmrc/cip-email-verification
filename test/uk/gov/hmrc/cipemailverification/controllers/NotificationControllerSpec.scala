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

package uk.gov.hmrc.cipemailverification.controllers

import org.mockito.ArgumentMatchersSugar.any
import org.mockito.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Reads
import play.api.mvc.AnyContentAsEmpty
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.cipemailverification.models.api.ErrorResponse.Codes.{Code, EXTERNAL_SERVER_FAIL_FORBIDDEN, EXTERNAL_SERVER_UNREACHABLE, NOTIFICATION_NOT_FOUND, VALIDATION_ERROR}
import uk.gov.hmrc.cipemailverification.models.api.ErrorResponse.Messages.{ENTER_A_VALID_NOTIFICATION_ID, EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE, Message, NOTIFICATION_ID_NOT_FOUND, SERVER_EXPERIENCED_AN_ISSUE}
import uk.gov.hmrc.cipemailverification.models.api.NotificationStatus
import uk.gov.hmrc.cipemailverification.models.api.NotificationStatus.Messages
import uk.gov.hmrc.cipemailverification.models.api.NotificationStatus.Statuses.{CREATED, DELIVERED, PENDING, PERMANENT_FAILURE, SENDING, SENT, Status, TECHNICAL_FAILURE, TEMPORARY_FAILURE}
import uk.gov.hmrc.cipemailverification.models.domain.result._
import uk.gov.hmrc.cipemailverification.services.NotificationService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.internalauth.client.Predicate.Permission
import uk.gov.hmrc.internalauth.client._
import uk.gov.hmrc.internalauth.client.test.{BackendAuthComponentsStub, StubBehaviour}

import scala.concurrent.ExecutionContext.Implicits
import scala.concurrent.Future

class NotificationControllerSpec extends AnyWordSpec
  with Matchers
  with IdiomaticMockito {

  "status" should {
    "return Ok with NotificationStatus when notification service returns Created" in new SetUp {
      val notificationId = "notificationId"
      mockNotificationsService.status(notificationId)(any[HeaderCarrier])
        .returns(Future.successful(Right(Created)))

      private val result = controller.status(notificationId)(fakeRequest)
      status(result) shouldBe OK
      (contentAsJson(result) \ "notificationStatus").as[Status] shouldBe CREATED
      (contentAsJson(result) \ "message").as[Message] shouldBe Messages.CREATED

      mockNotificationsService.status(notificationId)(any[HeaderCarrier]) was called
    }

    "return Ok with NotificationStatus when notification service returns Sending" in new SetUp {
      val notificationId = "notificationId"
      mockNotificationsService.status(notificationId)(any[HeaderCarrier])
        .returns(Future.successful(Right(Sending)))

      private val result = controller.status(notificationId)(fakeRequest)
      status(result) shouldBe OK
      (contentAsJson(result) \ "notificationStatus").as[Status] shouldBe SENDING
      (contentAsJson(result) \ "message").as[Message] shouldBe Messages.SENDING

      mockNotificationsService.status(notificationId)(any[HeaderCarrier]) was called
    }

    "return Ok with NotificationStatus when notification service returns Pending" in new SetUp {
      val notificationId = "notificationId"
      mockNotificationsService.status(notificationId)(any[HeaderCarrier])
        .returns(Future.successful(Right(Pending)))

      private val result = controller.status(notificationId)(fakeRequest)
      status(result) shouldBe OK
      (contentAsJson(result) \ "notificationStatus").as[Status] shouldBe PENDING
      (contentAsJson(result) \ "message").as[Message] shouldBe Messages.PENDING

      mockNotificationsService.status(notificationId)(any[HeaderCarrier]) was called
    }

    "return Ok with NotificationStatus when notification service returns Sent" in new SetUp {
      val notificationId = "notificationId"
      mockNotificationsService.status(notificationId)(any[HeaderCarrier])
        .returns(Future.successful(Right(Sent)))

      private val result = controller.status(notificationId)(fakeRequest)
      status(result) shouldBe OK
      (contentAsJson(result) \ "notificationStatus").as[Status] shouldBe SENT
      (contentAsJson(result) \ "message").as[Message] shouldBe Messages.SENT

      mockNotificationsService.status(notificationId)(any[HeaderCarrier]) was called
    }

    "return Ok with NotificationStatus when notification service returns Delivered" in new SetUp {
      val notificationId = "notificationId"
      mockNotificationsService.status(notificationId)(any[HeaderCarrier])
        .returns(Future.successful(Right(Delivered)))

      private val result = controller.status(notificationId)(fakeRequest)
      status(result) shouldBe OK
      (contentAsJson(result) \ "notificationStatus").as[Status] shouldBe DELIVERED
      (contentAsJson(result) \ "message").as[Message] shouldBe Messages.DELIVERED

      mockNotificationsService.status(notificationId)(any[HeaderCarrier]) was called
    }

    "return Ok with NotificationStatus when notification service returns PermanentFailure" in new SetUp {
      val notificationId = "notificationId"
      mockNotificationsService.status(notificationId)(any[HeaderCarrier])
        .returns(Future.successful(Right(PermanentFailure)))

      private val result = controller.status(notificationId)(fakeRequest)
      status(result) shouldBe OK
      (contentAsJson(result) \ "notificationStatus").as[Status] shouldBe PERMANENT_FAILURE
      (contentAsJson(result) \ "message").as[Message] shouldBe Messages.PERMANENT_FAILURE

      mockNotificationsService.status(notificationId)(any[HeaderCarrier]) was called
    }

    "return Ok with NotificationStatus when notification service returns TemporaryFailure" in new SetUp {
      val notificationId = "notificationId"
      mockNotificationsService.status(notificationId)(any[HeaderCarrier])
        .returns(Future.successful(Right(TemporaryFailure)))

      private val result = controller.status(notificationId)(fakeRequest)
      status(result) shouldBe OK
      (contentAsJson(result) \ "notificationStatus").as[Status] shouldBe TEMPORARY_FAILURE
      (contentAsJson(result) \ "message").as[Message] shouldBe Messages.TEMPORARY_FAILURE

      mockNotificationsService.status(notificationId)(any[HeaderCarrier]) was called
    }

    "return Ok with NotificationStatus when notification service returns TechnicalFailure" in new SetUp {
      val notificationId = "notificationId"
      mockNotificationsService.status(notificationId)(any[HeaderCarrier])
        .returns(Future.successful(Right(TechnicalFailure)))

      private val result = controller.status(notificationId)(fakeRequest)
      status(result) shouldBe OK
      (contentAsJson(result) \ "notificationStatus").as[Status] shouldBe TECHNICAL_FAILURE
      (contentAsJson(result) \ "message").as[Message] shouldBe Messages.TECHNICAL_FAILURE

      mockNotificationsService.status(notificationId)(any[HeaderCarrier]) was called
    }

    "return NotFound with ErrorResponse when notification service returns NotFound" in new SetUp {
      val notificationId = "notificationId"
      mockNotificationsService.status(notificationId)(any[HeaderCarrier])
        .returns(Future.successful(Left(NotFound)))

      private val result = controller.status(notificationId)(fakeRequest)
      status(result) shouldBe NOT_FOUND
      (contentAsJson(result) \ "code").as[Code] shouldBe NOTIFICATION_NOT_FOUND
      (contentAsJson(result) \ "message").as[Message] shouldBe NOTIFICATION_ID_NOT_FOUND

      mockNotificationsService.status(notificationId)(any[HeaderCarrier]) was called
    }

    "return BadRequest with ErrorResponse when notification service returns ValidationError" in new SetUp {
      val notificationId = "notificationId"
      mockNotificationsService.status(notificationId)(any[HeaderCarrier])
        .returns(Future.successful(Left(ValidationError)))

      private val result = controller.status(notificationId)(fakeRequest)
      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "code").as[Code] shouldBe VALIDATION_ERROR
      (contentAsJson(result) \ "message").as[Message] shouldBe ENTER_A_VALID_NOTIFICATION_ID

      mockNotificationsService.status(notificationId)(any[HeaderCarrier]) was called
    }

    "return InternalServerError with ErrorResponse when notification service returns GovNotifyForbidden" in new SetUp {
      val notificationId = "notificationId"
      mockNotificationsService.status(notificationId)(any[HeaderCarrier])
        .returns(Future.successful(Left(GovNotifyForbidden)))

      private val result = controller.status(notificationId)(fakeRequest)
      status(result) shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "code").as[Code] shouldBe EXTERNAL_SERVER_FAIL_FORBIDDEN
      (contentAsJson(result) \ "message").as[Message] shouldBe SERVER_EXPERIENCED_AN_ISSUE

      mockNotificationsService.status(notificationId)(any[HeaderCarrier]) was called
    }

    "return GatewayTimeout with ErrorResponse when notification service returns GovNotifyServiceDown" in new SetUp {
      val notificationId = "notificationId"
      mockNotificationsService.status(notificationId)(any[HeaderCarrier])
        .returns(Future.successful(Left(GovNotifyServiceDown)))

      private val result = controller.status(notificationId)(fakeRequest)
      status(result) shouldBe GATEWAY_TIMEOUT
      (contentAsJson(result) \ "code").as[Code] shouldBe EXTERNAL_SERVER_UNREACHABLE
      (contentAsJson(result) \ "message").as[Message] shouldBe EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE

      mockNotificationsService.status(notificationId)(any[HeaderCarrier]) was called
    }
  }

  trait SetUp {
    protected val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withHeaders("Authorization" -> "fake-token")
    private val expectedPredicate = {
      Permission(Resource(ResourceType("cip-email-verification"), ResourceLocation("*")), IAAction("*"))
    }
    protected val mockStubBehaviour: StubBehaviour = mock[StubBehaviour]
    protected val mockNotificationsService: NotificationService = mock[NotificationService]
    mockStubBehaviour.stubAuth(Some(expectedPredicate), Retrieval.EmptyRetrieval).returns(Future.unit)
    protected val backendAuthComponentsStub: BackendAuthComponents =
      BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), Implicits.global)
    protected val controller = new NotificationController(Helpers.stubControllerComponents(), mockNotificationsService, backendAuthComponentsStub)
    protected implicit val reads: Reads[NotificationStatus.Statuses.Value] = Reads.enumNameReads(NotificationStatus.Statuses)
  }
}
