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

package uk.gov.hmrc.cipemailverification.services

import play.api.Logging
import uk.gov.hmrc.cipemailverification.models.domain.data.EmailAndPasscodeData
import uk.gov.hmrc.cipemailverification.repositories.PasscodeCacheRepository
import uk.gov.hmrc.mongo.cache.DataKey

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PasscodeService @Inject()(passcodeCacheRepository: PasscodeCacheRepository)
                               (implicit ec: ExecutionContext) extends Logging {
  def persistPasscode(emailPasscodeData: EmailAndPasscodeData): Future[EmailAndPasscodeData] = {
    logger.debug(s"Storing emailPasscodeData in database")
    passcodeCacheRepository.put(emailPasscodeData.email)(DataKey("cip-email-verification"), emailPasscodeData)
      .map(_ => emailPasscodeData)
  }

  def retrievePasscode(email: String): Future[Option[EmailAndPasscodeData]] = {
    logger.debug(s"Retrieving emailPasscodeData from database")
    passcodeCacheRepository.get[EmailAndPasscodeData](email)(DataKey("cip-email-verification"))
  }
}
