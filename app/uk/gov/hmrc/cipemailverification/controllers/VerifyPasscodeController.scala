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

import play.api.Logging
import play.api.libs.json._
import play.api.mvc.{Action, ControllerComponents, Request, Result}
import uk.gov.hmrc.cipemailverification.controllers.InternalAuthAccess.permission
import uk.gov.hmrc.cipemailverification.models.api.ErrorResponse.Codes.{PASSCODE_CHECK_FAIL, PASSCODE_ENTERED_EXPIRED, PASSCODE_NOT_FOUND, SERVER_UNREACHABLE, VALIDATION_ERROR}
import uk.gov.hmrc.cipemailverification.models.api.ErrorResponse.Messages.{ENTER_A_CORRECT_PASSCODE, ENTER_A_VALID_EMAIL, PASSCODE_ALLOWED_TIME_ELAPSED, SERVER_CURRENTLY_UNAVAILABLE, SERVER_EXPERIENCED_AN_ISSUE}
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
    withJsonBody[EmailAndPasscode] {
      emailAndPasscode =>
        service.verifyPasscode(emailAndPasscode) map {
          case Right(Verified) => Ok(Json.toJson(VerificationStatus(VERIFIED)))
          case Right(PasscodeExpired) => Ok(Json.toJson(
            ErrorResponse(PASSCODE_ENTERED_EXPIRED, PASSCODE_ALLOWED_TIME_ELAPSED)))
          case Left(uk.gov.hmrc.cipemailverification.models.domain.result.NotFound) => Ok(Json.toJson(
            ErrorResponse(PASSCODE_NOT_FOUND, ENTER_A_CORRECT_PASSCODE)))
          case Right(NotVerified) => Ok(Json.toJson(VerificationStatus(NOT_VERIFIED)))
          case Left(ValidationError) => BadRequest(Json.toJson(ErrorResponse(VALIDATION_ERROR, ENTER_A_VALID_EMAIL)))
          case Left(DatabaseServiceDown) => InternalServerError(Json.toJson(
            ErrorResponse(PASSCODE_CHECK_FAIL, SERVER_EXPERIENCED_AN_ISSUE)))
          case Left(ValidationServiceError) => BadGateway(Json.toJson(
            ErrorResponse(SERVER_UNREACHABLE, SERVER_CURRENTLY_UNAVAILABLE)))
          case Left(ValidationServiceDown) => ServiceUnavailable(Json.toJson(
            ErrorResponse(SERVER_UNREACHABLE, SERVER_CURRENTLY_UNAVAILABLE)))
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
