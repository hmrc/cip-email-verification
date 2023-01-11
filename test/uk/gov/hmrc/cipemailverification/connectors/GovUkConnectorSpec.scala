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

package uk.gov.hmrc.cipemailverification.connectors

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.IdiomaticMockito
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.http.Status.OK
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.cipemailverification.config.{AppConfig, CircuitBreakerConfig, GovNotifyConfig}
import uk.gov.hmrc.cipemailverification.models.domain.data.EmailAndPasscodeData
import uk.gov.hmrc.cipemailverification.utils.TestActorSystem
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class GovUkConnectorSpec extends AnyWordSpec
  with Matchers
  with WireMockSupport
  with ScalaFutures
  with HttpClientV2Support
  with TestActorSystem
  with IdiomaticMockito {

  val notificationId = "test-notification-id"
  val emailUrl: String = "/v2/notifications/email"
  val notificationsUrl: String = s"/v2/notifications/$notificationId"

  "notificationStatus" should {
    "delegate to http client" in new SetUp {
      stubFor(
        WireMock.get(urlEqualTo(notificationsUrl))
          .willReturn(aResponse())
      )

      appConfigMock.govNotifyConfig.returns(GovNotifyConfig(
        "http", wireMockHost, wireMockPort, "template-id-fake", "", UUID.randomUUID().toString, cbConfigData))

      appConfigMock.cacheExpiry.returns(1)

      private val result = govUkConnector.notificationStatus(notificationId)
      await(result).status shouldBe OK

      verify(
        getRequestedFor(urlEqualTo(notificationsUrl))
      )
    }
  }

  "sendPasscode" should {
    "delegate to http client" in new SetUp {
      stubFor(
        post(urlEqualTo(emailUrl))
          .willReturn(aResponse())
      )

      appConfigMock.govNotifyConfig.returns(GovNotifyConfig(
        "http", wireMockHost, wireMockPort, "template-id-fake", "", UUID.randomUUID().toString, cbConfigData))

      appConfigMock.passcodeExpiry.returns(15)

      private val emailPasscodeData = EmailAndPasscodeData("test@test.com", "testPasscode", System.currentTimeMillis())

      private val result = govUkConnector.sendPasscode(emailPasscodeData)
      await(result).status shouldBe OK

      verify(
        postRequestedFor(urlEqualTo(emailUrl)).withRequestBody(equalToJson(
          """
            {
              "email_address": "test@test.com",
              "template_id": "template-id-fake",
              "personalisation": {
                "clientServiceName": "cip-email-service",
                "passcode": "testPasscode",
                "timeToLive": "15"
              }
            }
            """))
      )
    }
  }

  trait SetUp {
    protected implicit val hc: HeaderCarrier = HeaderCarrier()

    protected val appConfigMock: AppConfig = mock[AppConfig]
    protected val cbConfigData: CircuitBreakerConfig =
      CircuitBreakerConfig("", 5, 5.seconds, 30.seconds, 5.seconds, 1, 0)

    val govUkConnector = new GovUkConnector(
      httpClientV2,
      appConfigMock
    )
  }
}
