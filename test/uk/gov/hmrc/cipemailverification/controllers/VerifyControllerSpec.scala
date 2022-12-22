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

package uk.gov.hmrc.cipemailverification.controllers

import org.mockito.ArgumentMatchersSugar.any
import org.mockito.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.http.Status._
import play.api.libs.json.{Json, OWrites}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.Helpers.{LOCATION, contentAsJson, contentAsString, defaultAwaitTimeout, header, status}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.cipemailverification.models.api.Email
import uk.gov.hmrc.cipemailverification.models.api.ErrorResponse.Codes.{Code, EXTERNAL_SERVER_ERROR, EXTERNAL_SERVER_FAIL_FORBIDDEN, EXTERNAL_SERVER_FAIL_VALIDATION, EXTERNAL_SERVER_UNREACHABLE, MESSAGE_THROTTLED_OUT, PASSCODE_PERSISTING_FAIL, SERVER_ERROR, SERVER_UNREACHABLE, VALIDATION_ERROR}
import uk.gov.hmrc.cipemailverification.models.api.ErrorResponse.Messages.{ENTER_A_VALID_EMAIL, EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE, EXTERNAL_SERVER_EXPERIENCED_AN_ISSUE, Message, SERVER_CURRENTLY_UNAVAILABLE, SERVER_EXPERIENCED_AN_ISSUE, THROTTLED_TOO_MANY_REQUESTS}
import uk.gov.hmrc.cipemailverification.models.domain.result._
import uk.gov.hmrc.cipemailverification.models.http.govnotify.GovUkNotificationId
import uk.gov.hmrc.cipemailverification.services.VerifyService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.internalauth.client.Predicate.Permission
import uk.gov.hmrc.internalauth.client._
import uk.gov.hmrc.internalauth.client.test.{BackendAuthComponentsStub, StubBehaviour}

import scala.concurrent.ExecutionContext.Implicits
import scala.concurrent.Future

class VerifyControllerSpec
  extends AnyWordSpec
    with Matchers
    with IdiomaticMockito {

  "verify" should {
    "return Accepted when verify service returns PasscodeSent" in new SetUp {
      private val email = Email("test@test.test")
      private val expectedNotificationId = GovUkNotificationId("test-notification-id")
      mockVerifyService.verifyEmail(email)(any[HeaderCarrier])
        .returns(Future.successful(Right(PasscodeSent(expectedNotificationId))))
      private val result = controller.verify(
        fakeRequest.withBody(Json.toJson(email))
      )
      status(result) shouldBe ACCEPTED
      header(LOCATION, result) shouldBe Some(s"/notifications/${expectedNotificationId.id}")
      contentAsString(result) shouldBe empty

      mockVerifyService.verifyEmail(email)(any[HeaderCarrier]) was called
    }

    "return BadRequest when verify service returns ValidationError" in new SetUp {
      private val email = Email("test@test.test")
      mockVerifyService.verifyEmail(email)(any[HeaderCarrier])
        .returns(Future.successful(Left(ValidationError)))
      private val result = controller.verify(
        fakeRequest.withBody(Json.toJson(email))
      )
      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "code").as[Code] shouldBe VALIDATION_ERROR
      (contentAsJson(result) \ "message").as[Message] shouldBe ENTER_A_VALID_EMAIL

      mockVerifyService.verifyEmail(email)(any[HeaderCarrier]) was called
    }

    "return InternalServerError when verify service returns DatabaseServiceDown" in new SetUp {
      private val email = Email("test@test.test")
      mockVerifyService.verifyEmail(email)(any[HeaderCarrier])
        .returns(Future.successful(Left(DatabaseServiceDown)))
      private val result = controller.verify(
        fakeRequest.withBody(Json.toJson(email))
      )
      status(result) shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "code").as[Code] shouldBe PASSCODE_PERSISTING_FAIL
      (contentAsJson(result) \ "message").as[Message] shouldBe SERVER_EXPERIENCED_AN_ISSUE

      mockVerifyService.verifyEmail(email)(any[HeaderCarrier]) was called
    }

    "return BadGateway when verify service returns ValidationServiceError" in new SetUp {
      private val email = Email("test@test.test")
      mockVerifyService.verifyEmail(email)(any[HeaderCarrier])
        .returns(Future.successful(Left(ValidationServiceError)))
      private val result = controller.verify(
        fakeRequest.withBody(Json.toJson(email))
      )
      status(result) shouldBe BAD_GATEWAY
      (contentAsJson(result) \ "code").as[Code] shouldBe SERVER_ERROR
      (contentAsJson(result) \ "message").as[Message] shouldBe SERVER_EXPERIENCED_AN_ISSUE

      mockVerifyService.verifyEmail(email)(any[HeaderCarrier]) was called
    }

    "return ServiceUnavailable when verify service returns ValidationServiceDown" in new SetUp {
      private val email = Email("test@test.test")
      mockVerifyService.verifyEmail(email)(any[HeaderCarrier])
        .returns(Future.successful(Left(ValidationServiceDown)))
      private val result = controller.verify(
        fakeRequest.withBody(Json.toJson(email))
      )
      status(result) shouldBe SERVICE_UNAVAILABLE
      (contentAsJson(result) \ "code").as[Code] shouldBe SERVER_UNREACHABLE
      (contentAsJson(result) \ "message").as[Message] shouldBe SERVER_CURRENTLY_UNAVAILABLE

      mockVerifyService.verifyEmail(email)(any[HeaderCarrier]) was called
    }

    "return ServiceUnavailable when verify service returns GovNotifyServiceDown" in new SetUp {
      private val email = Email("test@test.test")
      mockVerifyService.verifyEmail(email)(any[HeaderCarrier])
        .returns(Future.successful(Left(GovNotifyServiceDown)))
      private val result = controller.verify(
        fakeRequest.withBody(Json.toJson(email))
      )
      status(result) shouldBe SERVICE_UNAVAILABLE
      (contentAsJson(result) \ "code").as[Code] shouldBe EXTERNAL_SERVER_UNREACHABLE
      (contentAsJson(result) \ "message").as[Message] shouldBe EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE

      mockVerifyService.verifyEmail(email)(any[HeaderCarrier]) was called
    }

    "return BadGateway when verify service returns GovNotifyServerError" in new SetUp {
      private val email = Email("test@test.test")
      mockVerifyService.verifyEmail(email)(any[HeaderCarrier])
        .returns(Future.successful(Left(GovNotifyServerError)))
      private val result = controller.verify(
        fakeRequest.withBody(Json.toJson(email))
      )
      status(result) shouldBe BAD_GATEWAY
      (contentAsJson(result) \ "code").as[Code] shouldBe EXTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "message").as[Message] shouldBe EXTERNAL_SERVER_EXPERIENCED_AN_ISSUE

      mockVerifyService.verifyEmail(email)(any[HeaderCarrier]) was called
    }

    "return ServiceUnavailable when verify service returns GovNotifyBadRequest" in new SetUp {
      private val email = Email("test@test.test")
      mockVerifyService.verifyEmail(email)(any[HeaderCarrier])
        .returns(Future.successful(Left(GovNotifyBadRequest)))
      private val result = controller.verify(
        fakeRequest.withBody(Json.toJson(email))
      )
      status(result) shouldBe SERVICE_UNAVAILABLE
      (contentAsJson(result) \ "code").as[Code] shouldBe EXTERNAL_SERVER_FAIL_VALIDATION
      (contentAsJson(result) \ "message").as[Message] shouldBe SERVER_EXPERIENCED_AN_ISSUE

      mockVerifyService.verifyEmail(email)(any[HeaderCarrier]) was called
    }

    "return ServiceUnavailable when verify service returns GovNotifyForbidden" in new SetUp {
      private val email = Email("test@test.test")
      mockVerifyService.verifyEmail(email)(any[HeaderCarrier])
        .returns(Future.successful(Left(GovNotifyForbidden)))
      private val result = controller.verify(
        fakeRequest.withBody(Json.toJson(email))
      )
      status(result) shouldBe SERVICE_UNAVAILABLE
      (contentAsJson(result) \ "code").as[Code] shouldBe EXTERNAL_SERVER_FAIL_FORBIDDEN
      (contentAsJson(result) \ "message").as[Message] shouldBe SERVER_EXPERIENCED_AN_ISSUE

      mockVerifyService.verifyEmail(email)(any[HeaderCarrier]) was called
    }

    "return TooManyRequests when verify service returns GovNotifyTooManyRequests" in new SetUp {
      private val email = Email("test@test.test")
      mockVerifyService.verifyEmail(email)(any[HeaderCarrier])
        .returns(Future.successful(Left(GovNotifyTooManyRequests)))
      private val result = controller.verify(
        fakeRequest.withBody(Json.toJson(email))
      )
      status(result) shouldBe TOO_MANY_REQUESTS
      (contentAsJson(result) \ "code").as[Code] shouldBe MESSAGE_THROTTLED_OUT
      (contentAsJson(result) \ "message").as[Message] shouldBe THROTTLED_TOO_MANY_REQUESTS

      mockVerifyService.verifyEmail(email)(any[HeaderCarrier]) was called
    }
  }

  trait SetUp {
    protected implicit val writes: OWrites[Email] = Json.writes[Email]
    protected val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withHeaders("Authorization" -> "fake-token")
    protected val mockVerifyService: VerifyService = mock[VerifyService]
    private val expectedPredicate = {
      Permission(Resource(ResourceType("cip-email-verification"), ResourceLocation("*")), IAAction("*"))
    }
    protected val mockStubBehaviour: StubBehaviour = mock[StubBehaviour]
    mockStubBehaviour.stubAuth(Some(expectedPredicate), Retrieval.EmptyRetrieval).returns(Future.unit)
    protected val backendAuthComponentsStub: BackendAuthComponents = BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), Implicits.global)
    protected val controller = new VerifyController(Helpers.stubControllerComponents(), mockVerifyService, backendAuthComponentsStub)
  }
}
