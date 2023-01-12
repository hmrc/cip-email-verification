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
import play.api.http.Status._
import play.api.libs.json.{Json, OWrites}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.Helpers.{contentAsJson, defaultAwaitTimeout, status}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.cipemailverification.models.api.EmailAndPasscode
import uk.gov.hmrc.cipemailverification.models.api.ErrorResponse.Codes.{Code, EXTERNAL_SERVER_UNREACHABLE, PASSCODE_CHECK_FAIL, PASSCODE_ENTERED_EXPIRED, PASSCODE_NOT_FOUND, SERVER_ERROR, SERVER_UNREACHABLE, VALIDATION_ERROR}
import uk.gov.hmrc.cipemailverification.models.api.ErrorResponse.Messages.{ENTER_A_CORRECT_PASSCODE, ENTER_A_VALID_EMAIL, EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE, Message, PASSCODE_ALLOWED_TIME_ELAPSED, PASSCODE_CHECK_ERROR, SERVER_CURRENTLY_UNAVAILABLE, SERVER_EXPERIENCED_AN_ISSUE}
import uk.gov.hmrc.cipemailverification.models.api.VerificationStatus.Messages.{NOT_VERIFIED, VERIFIED}
import uk.gov.hmrc.cipemailverification.models.domain.result._
import uk.gov.hmrc.cipemailverification.services.VerifyService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.internalauth.client.Predicate.Permission
import uk.gov.hmrc.internalauth.client._
import uk.gov.hmrc.internalauth.client.test.{BackendAuthComponentsStub, StubBehaviour}

import scala.concurrent.ExecutionContext.Implicits
import scala.concurrent.Future

class VerifyPasscodeControllerSpec
  extends AnyWordSpec
    with Matchers
    with IdiomaticMockito {

  "verifyPasscode" should {
    "return Ok with Verified status when verify service returns Verified" in new SetUp {
      private val emailAndPasscode = EmailAndPasscode("test", "123456")
      mockVerifyService.verifyPasscode(emailAndPasscode)(any[HeaderCarrier])
        .returns(Future.successful(Right(Verified)))
      private val result = controller.verifyPasscode(
        fakeRequest.withBody(Json.toJson(emailAndPasscode))
      )
      status(result) shouldBe OK
      (contentAsJson(result) \ "status").as[Message] shouldBe VERIFIED

      mockVerifyService.verifyPasscode(emailAndPasscode)(any[HeaderCarrier]) was called
    }

    "return Ok with error when verify service returns PasscodeExpired" in new SetUp {
      private val emailAndPasscode = EmailAndPasscode("test", "123456")
      mockVerifyService.verifyPasscode(emailAndPasscode)(any[HeaderCarrier])
        .returns(Future.successful(Right(PasscodeExpired)))
      private val result = controller.verifyPasscode(
        fakeRequest.withBody(Json.toJson(emailAndPasscode))
      )
      status(result) shouldBe OK
      (contentAsJson(result) \ "code").as[Code] shouldBe PASSCODE_ENTERED_EXPIRED
      (contentAsJson(result) \ "message").as[Message] shouldBe PASSCODE_ALLOWED_TIME_ELAPSED

      mockVerifyService.verifyPasscode(emailAndPasscode)(any[HeaderCarrier]) was called
    }

    "return Ok with error when verify service returns NotFound" in new SetUp {
      private val emailAndPasscode = EmailAndPasscode("test", "123456")
      mockVerifyService.verifyPasscode(emailAndPasscode)(any[HeaderCarrier])
        .returns(Future.successful(Left(NotFound)))
      private val result = controller.verifyPasscode(
        fakeRequest.withBody(Json.toJson(emailAndPasscode))
      )
      status(result) shouldBe OK
      (contentAsJson(result) \ "code").as[Code] shouldBe PASSCODE_NOT_FOUND
      (contentAsJson(result) \ "message").as[Message] shouldBe ENTER_A_CORRECT_PASSCODE

      mockVerifyService.verifyPasscode(emailAndPasscode)(any[HeaderCarrier]) was called
    }

    "return Ok with Not Verified status when verify service returns NotVerified" in new SetUp {
      private val emailAndPasscode = EmailAndPasscode("test", "123456")
      mockVerifyService.verifyPasscode(emailAndPasscode)(any[HeaderCarrier])
        .returns(Future.successful(Right(NotVerified)))
      private val result = controller.verifyPasscode(
        fakeRequest.withBody(Json.toJson(emailAndPasscode))
      )
      status(result) shouldBe OK
      (contentAsJson(result) \ "status").as[Message] shouldBe NOT_VERIFIED

      mockVerifyService.verifyPasscode(emailAndPasscode)(any[HeaderCarrier]) was called
    }

    "return BadRequest with error when verify service returns ValidationError" in new SetUp {
      private val emailAndPasscode = EmailAndPasscode("test", "123456")
      mockVerifyService.verifyPasscode(emailAndPasscode)(any[HeaderCarrier])
        .returns(Future.successful(Left(ValidationError)))
      private val result = controller.verifyPasscode(
        fakeRequest.withBody(Json.toJson(emailAndPasscode))
      )
      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "code").as[Code] shouldBe VALIDATION_ERROR
      (contentAsJson(result) \ "message").as[Message] shouldBe ENTER_A_VALID_EMAIL

      mockVerifyService.verifyPasscode(emailAndPasscode)(any[HeaderCarrier]) was called
    }

    "return GatewayTimeout when verify service returns DatabaseServiceDown" in new SetUp {
      private val emailAndPasscode = EmailAndPasscode("test", "123456")
      mockVerifyService.verifyPasscode(emailAndPasscode)(any[HeaderCarrier])
        .returns(Future.successful(Left(DatabaseServiceDown)))
      private val result = controller.verifyPasscode(
        fakeRequest.withBody(Json.toJson(emailAndPasscode))
      )
      status(result) shouldBe GATEWAY_TIMEOUT
      (contentAsJson(result) \ "code").as[Code] shouldBe EXTERNAL_SERVER_UNREACHABLE
      (contentAsJson(result) \ "message").as[Message] shouldBe EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE

      mockVerifyService.verifyPasscode(emailAndPasscode)(any[HeaderCarrier]) was called
    }

    "return InternalServerError when verify service returns DatabaseServiceError" in new SetUp {
      private val emailAndPasscode = EmailAndPasscode("test", "123456")
      mockVerifyService.verifyPasscode(emailAndPasscode)(any[HeaderCarrier])
        .returns(Future.successful(Left(DatabaseServiceError)))
      private val result = controller.verifyPasscode(
        fakeRequest.withBody(Json.toJson(emailAndPasscode))
      )
      status(result) shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "code").as[Code] shouldBe PASSCODE_CHECK_FAIL
      (contentAsJson(result) \ "message").as[Message] shouldBe PASSCODE_CHECK_ERROR

      mockVerifyService.verifyPasscode(emailAndPasscode)(any[HeaderCarrier]) was called
    }

    "return BadGateway when verify service returns ValidationServiceError" in new SetUp {
      private val emailAndPasscode = EmailAndPasscode("test", "123456")
      mockVerifyService.verifyPasscode(emailAndPasscode)(any[HeaderCarrier])
        .returns(Future.successful(Left(ValidationServiceError)))
      private val result = controller.verifyPasscode(
        fakeRequest.withBody(Json.toJson(emailAndPasscode))
      )
      status(result) shouldBe BAD_GATEWAY
      (contentAsJson(result) \ "code").as[Code] shouldBe SERVER_ERROR
      (contentAsJson(result) \ "message").as[Message] shouldBe SERVER_EXPERIENCED_AN_ISSUE

      mockVerifyService.verifyPasscode(emailAndPasscode)(any[HeaderCarrier]) was called
    }

    "return ServiceUnavailable when verify service returns ValidationServiceDown" in new SetUp {
      private val emailAndPasscode = EmailAndPasscode("test", "123456")
      mockVerifyService.verifyPasscode(emailAndPasscode)(any[HeaderCarrier])
        .returns(Future.successful(Left(ValidationServiceDown)))
      private val result = controller.verifyPasscode(
        fakeRequest.withBody(Json.toJson(emailAndPasscode))
      )
      status(result) shouldBe SERVICE_UNAVAILABLE
      (contentAsJson(result) \ "code").as[Code] shouldBe SERVER_UNREACHABLE
      (contentAsJson(result) \ "message").as[Message] shouldBe SERVER_CURRENTLY_UNAVAILABLE

      mockVerifyService.verifyPasscode(emailAndPasscode)(any[HeaderCarrier]) was called
    }
  }

  trait SetUp {
    protected implicit val writes: OWrites[EmailAndPasscode] = Json.writes[EmailAndPasscode]
    protected val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withHeaders("Authorization" -> "fake-token")
    protected val mockVerifyService: VerifyService = mock[VerifyService]
    private val expectedPredicate = {
      Permission(Resource(ResourceType("cip-email-verification"), ResourceLocation("*")), IAAction("*"))
    }
    protected val mockStubBehaviour: StubBehaviour = mock[StubBehaviour]
    mockStubBehaviour.stubAuth(Some(expectedPredicate), Retrieval.EmptyRetrieval).returns(Future.unit)
    protected val backendAuthComponentsStub: BackendAuthComponents = BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), Implicits.global)
    protected val controller = new VerifyPasscodeController(Helpers.stubControllerComponents(), mockVerifyService, backendAuthComponentsStub)
  }
}
