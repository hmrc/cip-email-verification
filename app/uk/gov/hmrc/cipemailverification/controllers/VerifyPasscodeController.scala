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

import play.api.Logging
import play.api.libs.json._
import play.api.mvc.{Action, ControllerComponents, Request, Result}
import uk.gov.hmrc.cipemailverification.controllers.InternalAuthAccess.permission
import uk.gov.hmrc.cipemailverification.models.api.ErrorResponse.Codes.{EXTERNAL_SERVER_UNREACHABLE, PASSCODE_CHECK_FAIL, PASSCODE_ENTERED_EXPIRED, PASSCODE_NOT_FOUND, SERVER_ERROR, SERVER_UNREACHABLE, VALIDATION_ERROR}
import uk.gov.hmrc.cipemailverification.models.api.ErrorResponse.Messages.{ENTER_A_CORRECT_PASSCODE, ENTER_A_VALID_EMAIL, EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE, PASSCODE_ALLOWED_TIME_ELAPSED, PASSCODE_CHECK_ERROR, SERVER_CURRENTLY_UNAVAILABLE, SERVER_EXPERIENCED_AN_ISSUE}
import uk.gov.hmrc.cipemailverification.models.api.VerificationStatus.Messages.{NOT_VERIFIED, VERIFIED}
import uk.gov.hmrc.cipemailverification.models.api.{EmailAndPasscode, ErrorResponse, VerificationStatus}
import uk.gov.hmrc.cipemailverification.models.domain.result._
import uk.gov.hmrc.cipemailverification.services.VerifyService
import uk.gov.hmrc.internalauth.client.BackendAuthComponents
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton()
class VerifyPasscodeController @Inject()(cc: ControllerComponents, service: VerifyService, auth: BackendAuthComponents)
  extends BackendController(cc)
    with Logging {

  def verifyPasscode: Action[JsValue] = auth.authorizedAction[Unit](permission).compose(Action(parse.json)).async { implicit request =>
    def onError(error: ApplicationError) = error match {
      case uk.gov.hmrc.cipemailverification.models.domain.result.NotFound => Ok(Json.toJson(
        ErrorResponse(PASSCODE_NOT_FOUND, ENTER_A_CORRECT_PASSCODE)))
      case ValidationError => BadRequest(Json.toJson(ErrorResponse(VALIDATION_ERROR, ENTER_A_VALID_EMAIL)))
      case DatabaseServiceDown => GatewayTimeout(Json.toJson(
        ErrorResponse(EXTERNAL_SERVER_UNREACHABLE, EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE)))
      case DatabaseServiceError => InternalServerError(Json.toJson(
        ErrorResponse(PASSCODE_CHECK_FAIL, PASSCODE_CHECK_ERROR)))
      case ValidationServiceError => BadGateway(Json.toJson(
        ErrorResponse(SERVER_ERROR, SERVER_EXPERIENCED_AN_ISSUE)))
      case ValidationServiceDown => ServiceUnavailable(Json.toJson(
        ErrorResponse(SERVER_UNREACHABLE, SERVER_CURRENTLY_UNAVAILABLE)))
    }

    def onSuccess(result: VerifyResult) = result match {
      case Verified => Ok(Json.toJson(VerificationStatus(VERIFIED)))
      case PasscodeExpired => Ok(Json.toJson(
        ErrorResponse(PASSCODE_ENTERED_EXPIRED, PASSCODE_ALLOWED_TIME_ELAPSED)))
      case NotVerified => Ok(Json.toJson(VerificationStatus(NOT_VERIFIED)))
    }

    withJsonBody[EmailAndPasscode] {
      emailAndPasscode =>
        service.verifyPasscode(emailAndPasscode) map {
          case Left(error) => onError(error)
          case Right(result) => onSuccess(result)
        }
    }
  }

  override protected def withJsonBody[T](f: T => Future[Result])(implicit request: Request[JsValue], m: Manifest[T], reads: Reads[T]): Future[Result] =
    request.body.validate[T] match {
      case JsSuccess(payload, _) => f(payload)
      case JsError(_) =>
        logger.warn(s"Failed to validate request")
        Future.successful(BadRequest(Json.toJson(ErrorResponse(VALIDATION_ERROR, "Enter a valid passcode"))))
    }
}
