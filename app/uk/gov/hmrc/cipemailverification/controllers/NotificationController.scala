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

package uk.gov.hmrc.cipemailverification.controllers

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.cipemailverification.models.api.ErrorResponse.Codes.{EXTERNAL_SERVER_FAIL_FORBIDDEN, EXTERNAL_SERVER_UNREACHABLE, NOTIFICATION_NOT_FOUND, VALIDATION_ERROR}
import uk.gov.hmrc.cipemailverification.models.api.ErrorResponse.Messages.{ENTER_A_VALID_NOTIFICATION_ID, EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE, NOTIFICATION_ID_NOT_FOUND, SERVER_EXPERIENCED_AN_ISSUE}
import uk.gov.hmrc.cipemailverification.models.api.NotificationStatus.{Messages, Statuses}
import uk.gov.hmrc.cipemailverification.models.api.{ErrorResponse, NotificationStatus}
import uk.gov.hmrc.cipemailverification.models.domain.result._
import uk.gov.hmrc.cipemailverification.services.NotificationService
import uk.gov.hmrc.internalauth.client.BackendAuthComponents
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton()
class NotificationController @Inject()(cc: ControllerComponents, notificationsService: NotificationService, auth: BackendAuthComponents)
  extends BackendController(cc) with InternalAuthAccess {

  def status(notificationId: String): Action[AnyContent] = auth.authorizedAction[Unit](permission).compose(Action).async { implicit request =>
    def onError(error: ApplicationError) = error match {
      case uk.gov.hmrc.cipemailverification.models.domain.result.NotFound =>
        NotFound(Json.toJson(ErrorResponse(NOTIFICATION_NOT_FOUND, NOTIFICATION_ID_NOT_FOUND)))
      case ValidationError => BadRequest(Json.toJson(ErrorResponse(VALIDATION_ERROR, ENTER_A_VALID_NOTIFICATION_ID)))
      case GovNotifyForbidden => InternalServerError(Json.toJson(
        ErrorResponse(EXTERNAL_SERVER_FAIL_FORBIDDEN, SERVER_EXPERIENCED_AN_ISSUE)))
      case GovNotifyServiceDown => GatewayTimeout(Json.toJson(
        ErrorResponse(EXTERNAL_SERVER_UNREACHABLE, EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE)))
    }

    def onSuccess(result: NotificationStatusResult) = result match {
      case uk.gov.hmrc.cipemailverification.models.domain.result.Created => Ok(Json.toJson(NotificationStatus(Statuses.CREATED, Messages.CREATED)))
      case Sending => Ok(Json.toJson(NotificationStatus(Statuses.SENDING, Messages.SENDING)))
      case Pending => Ok(Json.toJson(NotificationStatus(Statuses.PENDING, Messages.PENDING)))
      case Sent => Ok(Json.toJson(NotificationStatus(Statuses.SENT, Messages.SENT)))
      case Delivered => Ok(Json.toJson(NotificationStatus(Statuses.DELIVERED, Messages.DELIVERED)))
      case PermanentFailure => Ok(Json.toJson(NotificationStatus(Statuses.PERMANENT_FAILURE, Messages.PERMANENT_FAILURE)))
      case TemporaryFailure => Ok(Json.toJson(NotificationStatus(Statuses.TEMPORARY_FAILURE, Messages.TEMPORARY_FAILURE)))
      case TechnicalFailure => Ok(Json.toJson(NotificationStatus(Statuses.TECHNICAL_FAILURE, Messages.TECHNICAL_FAILURE)))
    }

    notificationsService.status(notificationId) map {
      case Left(error) => onError(error)
      case Right(result) => onSuccess(result)
    }
  }
}
