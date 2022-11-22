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
import play.api.http.Status.OK
import play.api.libs.json.{Json, OWrites}
import play.api.mvc.Results.Ok
import play.api.test.Helpers.{defaultAwaitTimeout, status}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.cipemailverification.models.api.Email
import uk.gov.hmrc.cipemailverification.services.VerifyService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.internalauth.client.Predicate.Permission
import uk.gov.hmrc.internalauth.client.test.{BackendAuthComponentsStub, StubBehaviour}
import uk.gov.hmrc.internalauth.client._

import scala.concurrent.ExecutionContext.Implicits
import scala.concurrent.Future

class VerifyControllerSpec
  extends AnyWordSpec
    with Matchers
    with IdiomaticMockito {

  "verify" should {
    "delegate to verify service" in new SetUp {
      val email = Email("test@test.test")
      mockVerifyService.verifyEmail(email)(any[HeaderCarrier])
        .returns(Future.successful(Ok))
      val result = controller.verify(
        fakeRequest.withBody(Json.toJson(email))
      )
      status(result) shouldBe OK
    }
  }

  trait SetUp {

    protected implicit val writes: OWrites[Email] = Json.writes[Email]
    protected val fakeRequest = FakeRequest().withHeaders("Authorization" -> "fake-token")
    protected val mockVerifyService = mock[VerifyService]

    val expectedPredicate = {
      Permission(Resource(ResourceType("cip-email-verification"), ResourceLocation("*")), IAAction("*"))
    }
    protected val mockStubBehaviour = mock[StubBehaviour]
    mockStubBehaviour.stubAuth(Some(expectedPredicate), Retrieval.EmptyRetrieval).returns(Future.unit)
    protected val backendAuthComponentsStub = BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), Implicits.global)
    protected val controller = new VerifyController(Helpers.stubControllerComponents(), mockVerifyService, backendAuthComponentsStub)
  }
}
