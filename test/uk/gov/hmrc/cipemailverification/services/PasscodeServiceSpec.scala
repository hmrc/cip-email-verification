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

import org.mockito.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.JsObject
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.cipemailverification.models.api.Email
import uk.gov.hmrc.cipemailverification.models.domain.data.EmailAndPasscodeData
import uk.gov.hmrc.cipemailverification.repositories.PasscodeCacheRepository
import uk.gov.hmrc.mongo.cache.{CacheItem, DataKey}

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PasscodeServiceSpec extends AnyWordSpec
  with Matchers
  with IdiomaticMockito {

  "persistPasscode" should {
    "return passcode" in new SetUp {
      private val email = Email("test@test.test")
      val passcode = "ABCDEF"
      private val emailPasscodeDataToPersist = EmailAndPasscodeData(email.email, passcode = passcode, System.currentTimeMillis())
      passcodeCacheRepositoryMock.put(email.email)(DataKey("cip-email-verification"), emailPasscodeDataToPersist)
        .returns(Future.successful(CacheItem("", JsObject.empty, Instant.EPOCH, Instant.EPOCH)))

      private val result = passcodeService.persistPasscode(emailPasscodeDataToPersist)

      await(result) shouldBe emailPasscodeDataToPersist
    }
  }

  "retrievePasscode" should {
    "return passcode" in new SetUp {
      private val now = System.currentTimeMillis()
      private val dataFromDb = EmailAndPasscodeData("test@test.test", "thePasscode", now)
      passcodeCacheRepositoryMock.get[EmailAndPasscodeData]("test@test.test")(DataKey("cip-email-verification"))
        .returns(Future.successful(Some(dataFromDb)))
      private val result = passcodeService.retrievePasscode("test@test.test")
      await(result) shouldBe Some(EmailAndPasscodeData("test@test.test", "thePasscode", now))
    }
  }

  trait SetUp {
    protected val passcodeCacheRepositoryMock: PasscodeCacheRepository = mock[PasscodeCacheRepository]
    protected val passcodeService: PasscodeService = new PasscodeService(passcodeCacheRepositoryMock)
  }
}
