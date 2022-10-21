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

import scala.concurrent.Future

class VerifyControllerSpec
  extends AnyWordSpec
    with Matchers
    with IdiomaticMockito {

  private implicit val writes: OWrites[Email] = Json.writes[Email]
  private val fakeRequest = FakeRequest()
  private val mockVerifyService = mock[VerifyService]
  private val controller = new VerifyController(Helpers.stubControllerComponents(), mockVerifyService)

  "verify" should {
    "delegate to verify service" in {
      val email = Email("test@test.test")
      mockVerifyService.verifyEmail(email)(any[HeaderCarrier])
        .returns(Future.successful(Ok))
      val result = controller.verify(
        fakeRequest.withBody(Json.toJson(email))
      )
      status(result) shouldBe OK
    }
  }
}
