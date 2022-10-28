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

package uk.gov.hmrc.cipemailverification

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json.Json
import play.api.libs.ws.ahc.AhcCurlRequestLogger
import uk.gov.hmrc.cipemailverification.models.api.ErrorResponse.Codes
import uk.gov.hmrc.cipemailverification.utils.DataSteps

class PasscodeIntegrationSpec
  extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneServerPerSuite
    with DataSteps {

  "/verify/passcode" should {
    "respond with 200 verified status with valid email and passcode" in {
      val email = "test@test.com"

      //generate EmailAndPasscode
      verify(email).futureValue

      //retrieve EmailAndPasscode
      val maybeEmailAndPasscode = retrievePasscode("test@test.com").futureValue

      //verify EmailAndPasscode (sut)
      val response =
        wsClient
          .url(s"$baseUrl/customer-insight-platform/email/verify/passcode")
          .withRequestFilter(AhcCurlRequestLogger())
          .post(Json.parse {
            s"""{
               "email": "$email",
               "passcode": "${maybeEmailAndPasscode.get.passcode}"
               }""".stripMargin
          })
          .futureValue

      response.status shouldBe 200
      (response.json \ "status").as[String] shouldBe "Verified"
    }
    
    "respond with 400 status for invalid request" in {
      val response =
        wsClient
          .url(s"$baseUrl/customer-insight-platform/email/verify/passcode")
          .withRequestFilter(AhcCurlRequestLogger())
          .post(Json.parse {
            s"""{
               "email": "test@test.com",
               "passcode": ""
               }""".stripMargin
          })
          .futureValue

      response.status shouldBe 400
      (response.json \ "code").as[Int] shouldBe Codes.VALIDATION_ERROR.id
      (response.json \ "message").as[String] shouldBe "Enter a valid passcode"
    }
  }
}
