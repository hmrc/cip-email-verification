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

package uk.gov.hmrc.cipemailverification.connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.IdiomaticMockito
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.http.Status.OK
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.cipemailverification.config.{AppConfig, CipValidationConfig, CircuitBreakerConfig}
import uk.gov.hmrc.cipemailverification.models.api.Email
import uk.gov.hmrc.cipemailverification.utils.TestActorSystem
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class ValidateConnectorSpec extends AnyWordSpec
  with Matchers
  with WireMockSupport
  with ScalaFutures
  with HttpClientV2Support
  with TestActorSystem
  with IdiomaticMockito {

  val url: String = "/customer-insight-platform/email/validate"

  "callService" should {
    "delegate to http client" in new SetUp {
      private val email = Email("test@test.test")

      stubFor(post(urlEqualTo(url)).willReturn(aResponse()))
      appConfigMock.validationConfig.returns(CipValidationConfig(
        "http", wireMockHost, wireMockPort, "fake-token", cbConfigData))
      appConfigMock.cacheExpiry.returns(1)

      private val result = validateConnector.callService(email.email)

      await(result).status shouldBe OK

      verify(
        postRequestedFor(urlEqualTo(url))
          .withRequestBody(equalToJson(s"""{"email": "${email.email}"}"""))
      )
    }
  }

  trait SetUp {
    protected implicit val hc: HeaderCarrier = HeaderCarrier()

    protected val appConfigMock: AppConfig = mock[AppConfig]
    protected val cipValidationConfigMock: CipValidationConfig = mock[CipValidationConfig]

    protected val cbConfigData: CircuitBreakerConfig =
      CircuitBreakerConfig("", 5, 5.minutes, 30.seconds, 5.minutes, 1, 0)

    appConfigMock.validationConfig returns cipValidationConfigMock
    cipValidationConfigMock.cbConfig returns cbConfigData
    cipValidationConfigMock.url returns wireMockUrl
    cipValidationConfigMock.authToken returns "fake-token"

    val validateConnector = new ValidateConnector(
      httpClientV2,
      appConfigMock
    )
  }
}
