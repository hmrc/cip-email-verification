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

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.cipemailverification.controllers.InternalAuthAccess.permission
import uk.gov.hmrc.cipemailverification.models.api.ErrorResponse.Codes.{EXTERNAL_SERVER_UNREACHABLE, NOTIFICATION_NOT_FOUND, VALIDATION_ERROR}
import uk.gov.hmrc.cipemailverification.models.api.ErrorResponse.Messages.{ENTER_A_VALID_NOTIFICATION_ID, EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE, NOTIFICATION_ID_NOT_FOUND}
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
  extends BackendController(cc) {

    def status(notificationId: String): Action[AnyContent] = auth.authorizedAction[Unit](permission).compose(Action).async { implicit request =>
    notificationsService.status(notificationId) map {
      case Right(uk.gov.hmrc.cipemailverification.models.domain.result.Created) => Ok(Json.toJson(NotificationStatus(Statuses.CREATED, Messages.CREATED)))
      case Right(Sending) => Ok(Json.toJson(NotificationStatus(Statuses.SENDING, Messages.SENDING)))
      case Right(Pending) => Ok(Json.toJson(NotificationStatus(Statuses.PENDING, Messages.PENDING)))
      case Right(Sent) => Ok(Json.toJson(NotificationStatus(Statuses.SENT, Messages.SENT)))
      case Right(Delivered) => Ok(Json.toJson(NotificationStatus(Statuses.DELIVERED, Messages.DELIVERED)))
      case Right(PermanentFailure) => Ok(Json.toJson(NotificationStatus(Statuses.PERMANENT_FAILURE, Messages.PERMANENT_FAILURE)))
      case Right(TemporaryFailure) => Ok(Json.toJson(NotificationStatus(Statuses.TEMPORARY_FAILURE, Messages.TEMPORARY_FAILURE)))
      case Right(TechnicalFailure) => Ok(Json.toJson(NotificationStatus(Statuses.TECHNICAL_FAILURE, Messages.TECHNICAL_FAILURE)))
      case Left(uk.gov.hmrc.cipemailverification.models.domain.result.NotFound) => NotFound(Json.toJson(ErrorResponse(NOTIFICATION_NOT_FOUND, NOTIFICATION_ID_NOT_FOUND)))
      case Left(ValidationError) => BadRequest(Json.toJson(ErrorResponse(VALIDATION_ERROR, ENTER_A_VALID_NOTIFICATION_ID)))
      case Left(GovNotifyForbidden) => ServiceUnavailable(Json.toJson(
        ErrorResponse(EXTERNAL_SERVER_UNREACHABLE, EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE)))
      case Left(GovNotifyServiceDown) => GatewayTimeout(Json.toJson(
        ErrorResponse(EXTERNAL_SERVER_UNREACHABLE, EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE)))
    }
  }
}
