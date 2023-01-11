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

import play.api.Logging
import play.api.http.Status._
import uk.gov.hmrc.cipemailverification.connectors.GovUkConnector
import uk.gov.hmrc.cipemailverification.models.domain.audit.AuditType.EmailVerificationDeliveryResultRequest
import uk.gov.hmrc.cipemailverification.models.domain.audit.VerificationDeliveryResultRequestAuditEvent
import uk.gov.hmrc.cipemailverification.models.domain.result._
import uk.gov.hmrc.cipemailverification.models.http.govnotify.GovUkNotificationStatusResponse
import uk.gov.hmrc.cipemailverification.utils.GovNotifyUtils
import uk.gov.hmrc.http.HttpReads.is2xx
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton()
class NotificationService @Inject()(govNotifyUtils: GovNotifyUtils, auditService: AuditService,
                                    govUkConnector: GovUkConnector)
                                   (implicit val executionContext: ExecutionContext) extends Logging {

  private val NO_DATA_FOUND = "No_data_found"

  def status(notificationId: String)(implicit hc: HeaderCarrier): Future[Either[ApplicationError, NotificationStatusResult]] = {
    def success(response: HttpResponse) = {
      val govNotifyResponse: GovUkNotificationStatusResponse = response.json.as[GovUkNotificationStatusResponse]
      val email = govNotifyResponse.email_address
      val passcode = govNotifyUtils.extractPasscodeFromGovNotifyBody(govNotifyResponse.body)
      val deliveryStatus = govNotifyResponse.status
      val result = deliveryStatus match {
        case "created" => Created
        case "sending" => Sending
        case "pending" => Pending
        case "sent" => Sent
        case "delivered" => Delivered
        case "permanent-failure" => PermanentFailure
        case "temporary-failure" => TemporaryFailure
        case "technical-failure" => TechnicalFailure
      }
      auditService.sendExplicitAuditEvent(EmailVerificationDeliveryResultRequest,
        VerificationDeliveryResultRequestAuditEvent(email, passcode, notificationId, deliveryStatus))

      Right(result)
    }

    def failure(err: HttpResponse) = {
      err.status match {
        case NOT_FOUND =>
          logger.warn("Notification Id not found")
          auditService.sendExplicitAuditEvent(EmailVerificationDeliveryResultRequest,
            VerificationDeliveryResultRequestAuditEvent(NO_DATA_FOUND, NO_DATA_FOUND, notificationId, NO_DATA_FOUND))
          Left(NotFound)
        case BAD_REQUEST =>
          logger.warn("Notification Id not valid")
          Left(ValidationError)
        case FORBIDDEN =>
          logger.warn(err.body)
          Left(GovNotifyForbidden)
        case _ =>
          logger.error(err.body)
          Left(GovNotifyServerError)
      }
    }

    govUkConnector.notificationStatus(notificationId) transformWith {
      case Success(response) => response match {
        case _ if is2xx(response.status) => Future.successful(success(response))
        case _ => Future.successful(failure(response))
      }
      case Failure(response) =>
        logger.error(response.getMessage)
        Future.successful(Left(GovNotifyServiceDown))
    }
  }
}
