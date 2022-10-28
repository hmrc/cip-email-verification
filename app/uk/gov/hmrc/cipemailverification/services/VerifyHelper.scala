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
import play.api.http.HttpEntity
import play.api.http.Status.{BAD_REQUEST, FORBIDDEN, INTERNAL_SERVER_ERROR, TOO_MANY_REQUESTS}
import play.api.libs.json.Json
import play.api.mvc.Results.{Accepted, BadGateway, BadRequest, InternalServerError, ServiceUnavailable, TooManyRequests}
import play.api.mvc.{ResponseHeader, Result}
import uk.gov.hmrc.cipemailverification.connectors.GovUkConnector
import uk.gov.hmrc.cipemailverification.models.EmailPasscodeData
import uk.gov.hmrc.cipemailverification.models.api.ErrorResponse.Codes
import uk.gov.hmrc.cipemailverification.models.api.ErrorResponse.Message._
import uk.gov.hmrc.cipemailverification.models.api.{Email, ErrorResponse}
import uk.gov.hmrc.cipemailverification.models.http.govnotify.GovUkNotificationId
import uk.gov.hmrc.cipemailverification.utils.DateTimeUtils
import uk.gov.hmrc.http.HttpReads.{is2xx, is4xx, is5xx}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

abstract class VerifyHelper @Inject()(passcodeGenerator: PasscodeGenerator,
                                      passcodeService: PasscodeService,
                                      govUkConnector: GovUkConnector,
                                      dateTimeUtils: DateTimeUtils)
                                     (implicit ec: ExecutionContext) extends Logging {

  protected def processResponse(res: HttpResponse)(implicit hc: HeaderCarrier): Future[Result] = res match {
    case _ if is2xx(res.status) => processValidEmailAddress(res.json.as[Email])
    case _ if is4xx(res.status) => Future(BadRequest(res.json))
    case _ if is5xx(res.status) => Future(BadGateway(
      Json.toJson(ErrorResponse(Codes.EXTERNAL_SERVER_ERROR.id, EXTERNAL_SERVER_EXPERIENCED_AN_ISSUE))
    ))
  }

  private def processValidEmailAddress(email: Email)(implicit hc: HeaderCarrier): Future[Result] = {
    val passcode = passcodeGenerator.passcodeGenerator()
    val now = dateTimeUtils.getCurrentDateTime()
    val dataToSave = new EmailPasscodeData(email.email, passcode, now)
    passcodeService.persistPasscode(dataToSave) transformWith {
      case Success(savedEmailPasscodeData) => sendPasscode(savedEmailPasscodeData)
      case Failure(err) =>
        logger.error(s"Database operation failed, ${err.getMessage}")
        Future.successful(InternalServerError(Json.toJson(ErrorResponse(Codes.PASSCODE_PERSISTING_FAIL.id, SERVER_EXPERIENCED_AN_ISSUE))))
    }
  }

    private def sendPasscode(data: EmailPasscodeData)
                            (implicit hc: HeaderCarrier) = govUkConnector.sendPasscode(data) map {
      case Left(error) => error.statusCode match {
        case INTERNAL_SERVER_ERROR =>
          logger.error(error.getMessage)
          BadGateway(Json.toJson(ErrorResponse(Codes.EXTERNAL_SERVER_ERROR.id, EXTERNAL_SERVER_EXPERIENCED_AN_ISSUE)))
        case BAD_REQUEST =>
          logger.error(error.getMessage)
          ServiceUnavailable(Json.toJson(ErrorResponse(Codes.EXTERNAL_SERVER_FAIL_VALIDATION.id, SERVER_EXPERIENCED_AN_ISSUE)))
        case FORBIDDEN =>
          logger.error(error.getMessage)
          ServiceUnavailable(Json.toJson(ErrorResponse(Codes.EXTERNAL_SERVER_FAIL_FORBIDDEN.id, SERVER_EXPERIENCED_AN_ISSUE)))
        case TOO_MANY_REQUESTS =>
          logger.error(error.getMessage)
          TooManyRequests(Json.toJson(ErrorResponse(Codes.MESSAGE_THROTTLED_OUT.id, THROTTLED_TOO_MANY_REQUESTS)))
        case _ =>
          logger.error(error.getMessage)
          Result.apply(ResponseHeader(error.statusCode), HttpEntity.NoEntity)
      }
      case Right(response) if response.status == 201 =>
        Accepted.withHeaders(("Location", s"/notifications/${response.json.as[GovUkNotificationId].id}"))
    } recover {
      case err =>
        logger.error(err.getMessage)
        ServiceUnavailable(Json.toJson(ErrorResponse(Codes.EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE.id, EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE)))
    }
}

