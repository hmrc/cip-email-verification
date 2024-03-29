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

import uk.gov.hmrc.cipemailverification.config.AppConfig
import uk.gov.hmrc.cipemailverification.connectors.{GovUkConnector, ValidateConnector}
import uk.gov.hmrc.cipemailverification.metrics.MetricsService
import uk.gov.hmrc.cipemailverification.models.api.{Email, EmailAndPasscode}
import uk.gov.hmrc.cipemailverification.models.domain.result.{ApplicationError, ValidationServiceDown, VerifyResult}
import uk.gov.hmrc.cipemailverification.utils.DateTimeUtils
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class VerifyService @Inject()(passcodeGenerator: PasscodeGenerator,
                              auditService: AuditService,
                              passcodeService: PasscodeService,
                              dateTimeUtils: DateTimeUtils,
                              metricsService: MetricsService,
                              govUkConnector: GovUkConnector,
                              validateConnector: ValidateConnector,
                              config: AppConfig)
                             (implicit val executionContext: ExecutionContext) extends VerifyHelper(passcodeGenerator,
  auditService, passcodeService, metricsService, govUkConnector, dateTimeUtils, config) {

  def verifyEmail(email: Email)(implicit hc: HeaderCarrier): Future[Either[ApplicationError, VerifyResult]] =
    validateConnector.callService(email.email) transformWith {
      case Success(httpResponse) => processResponse(httpResponse, email)
      case Failure(error) =>
        metricsService.recordMetric("CIP-Validation-HTTP-Failure")
        metricsService.recordMetric(error.toString.trim.dropRight(1))
        logger.error(error.getMessage)
        Future.successful(Left(ValidationServiceDown))
    }

  def verifyPasscode(emailAndPasscode: EmailAndPasscode)(implicit hc: HeaderCarrier): Future[Either[ApplicationError, VerifyResult]] = {
    validateConnector.callService(emailAndPasscode.email).transformWith {
      case Success(httpResponse) => processResponseForPasscode(httpResponse, emailAndPasscode)
      case Failure(error) =>
        metricsService.recordMetric("CIP-Validation-HTTP-Failure")
        metricsService.recordMetric(error.toString.trim.dropRight(1))
        logger.error(error.getMessage)
        Future.successful(Left(ValidationServiceDown))
    }
  }
}
