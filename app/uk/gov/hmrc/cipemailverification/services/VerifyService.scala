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

import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.mvc.Results._
import uk.gov.hmrc.cipemailverification.connectors.{GovUkConnector, ValidateConnector}
import uk.gov.hmrc.cipemailverification.models.api.ErrorResponse.Codes
import uk.gov.hmrc.cipemailverification.models.api.ErrorResponse.Message._
import uk.gov.hmrc.cipemailverification.models.api.{Email, ErrorResponse}
import uk.gov.hmrc.cipemailverification.utils.DateTimeUtils
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class VerifyService @Inject() (passcodeGenerator: PasscodeGenerator,
                               passcodeService: PasscodeService,
                               dateTimeUtils: DateTimeUtils,
                               govUkConnector: GovUkConnector,
                               validateConnector: ValidateConnector)(implicit val executionContext: ExecutionContext) extends
  VerifyHelper(passcodeGenerator, passcodeService, govUkConnector, dateTimeUtils) {

  def verifyEmail(email: Email)(implicit hc: HeaderCarrier): Future[Result] =
    validateConnector.callService(email.email) transformWith {
      case Success(httpResponse) => processResponse(httpResponse)
      case Failure(error) =>
        logger.error(error.getMessage)
        Future.successful(ServiceUnavailable(Json.toJson(ErrorResponse(Codes.SERVER_CURRENTLY_UNAVAILABLE.id, SERVER_CURRENTLY_UNAVAILABLE))))
    }
}

