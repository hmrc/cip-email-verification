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

import play.api.libs.json.{JsSuccess, JsValue, Json, Reads}
import play.api.mvc.{Action, ControllerComponents, Request, Result}
import uk.gov.hmrc.cipemailverification.controllers.InternalAuthAccess.permission
import uk.gov.hmrc.cipemailverification.models.api.ErrorResponse.Codes.{EXTERNAL_SERVER_ERROR, EXTERNAL_SERVER_FAIL_FORBIDDEN, EXTERNAL_SERVER_FAIL_VALIDATION, EXTERNAL_SERVER_UNREACHABLE, MESSAGE_THROTTLED_OUT, PASSCODE_PERSISTING_FAIL, REQUEST_STILL_PROCESSING, SERVER_ERROR, SERVER_UNREACHABLE, VALIDATION_ERROR}
import uk.gov.hmrc.cipemailverification.models.api.ErrorResponse.Messages.{PASSCODE_PERSIST_ERROR, ENTER_A_VALID_EMAIL, EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE, EXTERNAL_SERVER_EXPERIENCED_AN_ISSUE, REQUEST_IN_PROGRESS, SERVER_CURRENTLY_UNAVAILABLE, SERVER_EXPERIENCED_AN_ISSUE, THROTTLED_TOO_MANY_REQUESTS}
import uk.gov.hmrc.cipemailverification.models.api.{Email, ErrorResponse}
import uk.gov.hmrc.cipemailverification.models.domain.result._
import uk.gov.hmrc.cipemailverification.services.VerifyService
import uk.gov.hmrc.internalauth.client.BackendAuthComponents
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton()
class VerifyController @Inject()(cc: ControllerComponents, service: VerifyService, auth: BackendAuthComponents)
  extends BackendController(cc) {

  def verify: Action[JsValue] = auth.authorizedAction[Unit](permission).compose(Action(parse.json)).async { implicit request =>
    def onError(error: ApplicationError) = error match {
      case ValidationError => BadRequest(Json.toJson(ErrorResponse(VALIDATION_ERROR, ENTER_A_VALID_EMAIL)))
      case ValidationServiceError => BadGateway(Json.toJson(
        ErrorResponse(SERVER_ERROR, SERVER_EXPERIENCED_AN_ISSUE)))
      case ValidationServiceDown => ServiceUnavailable(Json.toJson(
        ErrorResponse(SERVER_UNREACHABLE, SERVER_CURRENTLY_UNAVAILABLE)))
      case DatabaseServiceDown => GatewayTimeout(Json.toJson(
        ErrorResponse(EXTERNAL_SERVER_UNREACHABLE, EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE)))
      case DatabaseServiceError => InternalServerError(Json.toJson(
        ErrorResponse(PASSCODE_PERSISTING_FAIL, PASSCODE_PERSIST_ERROR)))
      case GovNotifyServiceDown => ServiceUnavailable(Json.toJson(
        ErrorResponse(EXTERNAL_SERVER_UNREACHABLE, EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE)))
      case GovNotifyServerError => BadGateway(Json.toJson(
        ErrorResponse(EXTERNAL_SERVER_ERROR, EXTERNAL_SERVER_EXPERIENCED_AN_ISSUE)))
      case GovNotifyBadRequest => ServiceUnavailable(Json.toJson(
        ErrorResponse(EXTERNAL_SERVER_FAIL_VALIDATION, SERVER_EXPERIENCED_AN_ISSUE)))
      case GovNotifyForbidden => ServiceUnavailable(Json.toJson(
        ErrorResponse(EXTERNAL_SERVER_FAIL_FORBIDDEN, SERVER_EXPERIENCED_AN_ISSUE)))
      case GovNotifyTooManyRequests => TooManyRequests(Json.toJson(
        ErrorResponse(MESSAGE_THROTTLED_OUT, THROTTLED_TOO_MANY_REQUESTS)))
      case RequestInProgress => TooManyRequests(Json.toJson(
        ErrorResponse(REQUEST_STILL_PROCESSING, REQUEST_IN_PROGRESS)))
    }

    withJsonBody[Email] {
      email =>
        service.verifyEmail(email) map {
          case Right(PasscodeSent(notificationId)) =>
            Accepted.withHeaders((LOCATION, s"/notifications/${notificationId.id}"))
          case Left(error) => onError(error)
        }
    }
  }

  override protected def withJsonBody[T](f: T => Future[Result])
                                        (implicit request: Request[JsValue], m: Manifest[T], reads: Reads[T]): Future[Result] =
    request.body.validate[T] match {
      case JsSuccess(payload, _) => f(payload)
    }
}
