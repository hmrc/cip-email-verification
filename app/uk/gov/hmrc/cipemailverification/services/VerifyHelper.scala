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

import com.mongodb.MongoTimeoutException
import play.api.Logging
import play.api.http.Status.{BAD_REQUEST, FORBIDDEN, INTERNAL_SERVER_ERROR, TOO_MANY_REQUESTS}
import uk.gov.hmrc.cipemailverification.config.AppConfig
import uk.gov.hmrc.cipemailverification.connectors.GovUkConnector
import uk.gov.hmrc.cipemailverification.metrics.MetricsService
import uk.gov.hmrc.cipemailverification.models.api.VerificationStatus.Messages.{NOT_VERIFIED, VERIFIED}
import uk.gov.hmrc.cipemailverification.models.api.{Email, EmailAndPasscode}
import uk.gov.hmrc.cipemailverification.models.domain.audit.AuditType.{EmailVerificationCheck, EmailVerificationRequest}
import uk.gov.hmrc.cipemailverification.models.domain.audit.{VerificationCheckAuditEvent, VerificationRequestAuditEvent}
import uk.gov.hmrc.cipemailverification.models.domain.data.EmailAndPasscodeData
import uk.gov.hmrc.cipemailverification.models.domain.result._
import uk.gov.hmrc.cipemailverification.models.http.govnotify.GovUkNotificationId
import uk.gov.hmrc.cipemailverification.models.http.validation.ValidatedEmail
import uk.gov.hmrc.cipemailverification.utils.DateTimeUtils
import uk.gov.hmrc.http.HttpReads.{is2xx, is4xx, is5xx}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import java.time.Duration
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

abstract class VerifyHelper @Inject()(passcodeGenerator: PasscodeGenerator,
                                      auditService: AuditService,
                                      passcodeService: PasscodeService,
                                      metricsService: MetricsService,
                                      govUkConnector: GovUkConnector,
                                      dateTimeUtils: DateTimeUtils,
                                      config: AppConfig)
                                     (implicit ec: ExecutionContext) extends Logging {

  private val passcodeExpiry = config.passcodeExpiry

  protected def processResponse(res: HttpResponse, email: Email)
                               (implicit hc: HeaderCarrier): Future[Either[ApplicationError, VerifyResult]] = res match {
    case _ if is2xx(res.status) => processValidEmailAddress(email)
    case _ if is4xx(res.status) =>
      logger.warn(res.body)
      Future.successful(Left(ValidationError))
    case _ if is5xx(res.status) =>
      logger.error(res.body)
      Future.successful(Left(ValidationServiceError))
  }

  protected def processResponseForPasscode(res: HttpResponse, emailAndPasscode: EmailAndPasscode)
                                          (implicit hc: HeaderCarrier): Future[Either[ApplicationError, VerifyResult]] = res match {
    case _ if is2xx(res.status) => processValidPasscode(res.json.as[ValidatedEmail], emailAndPasscode.passcode)
    case _ if is4xx(res.status) =>
      logger.warn(res.body)
      Future.successful(Left(ValidationError))
    case _ if is5xx(res.status) =>
      logger.error(res.body)
      Future.successful(Left(ValidationServiceError))
  }

  private def processValidEmailAddress(email: Email)(implicit hc: HeaderCarrier): Future[Either[ApplicationError, VerifyResult]] = {
    val passcode = passcodeGenerator.passcodeGenerator()
    val now = dateTimeUtils.getCurrentDateTime()
    val dataToSave = new EmailAndPasscodeData(email.email, passcode, now)
    val MILLIS_IN_MIN = 60000

    auditService.sendExplicitAuditEvent(EmailVerificationRequest,
      VerificationRequestAuditEvent(dataToSave.email, passcode))

    passcodeService.retrievePasscode(email.email) transformWith {
      case Failure(err) => Future.successful(onDatabaseError(err))
      case Success(v) if v.isEmpty => persistAndSendPasscode(dataToSave)
      case Success(v) if (v.nonEmpty && (calculateElapsedTime(v.get.createdAt, now) > passcodeExpiry * MILLIS_IN_MIN)) =>
        persistAndSendPasscode(dataToSave)
      case Success(v) if (v.nonEmpty) => Future.successful(Left(RequestInProgress))
    }
  }

  private def processValidPasscode(validatedEmail: ValidatedEmail, passcode: String)
                                  (implicit hc: HeaderCarrier) = {
    (for {
      maybeEmailAndPasscodeData <- passcodeService.retrievePasscode(validatedEmail.email)
      result <- processPasscode(EmailAndPasscode(validatedEmail.email, passcode), maybeEmailAndPasscodeData)
    } yield result).recover {
      case err => onDatabaseError(err)
    }
  }

  private def processPasscode(enteredEmailAndPasscode: EmailAndPasscode,
                              maybeEmailAndPasscode: Option[EmailAndPasscodeData])
                             (implicit hc: HeaderCarrier) =
    maybeEmailAndPasscode match {
      case Some(storedEmailAndPasscode) => checkIfPasscodeIsStillAllowedToBeUsed(enteredEmailAndPasscode,
        storedEmailAndPasscode, System.currentTimeMillis())
      case _ =>
        auditService.sendExplicitAuditEvent(EmailVerificationCheck,
          VerificationCheckAuditEvent(enteredEmailAndPasscode.email, enteredEmailAndPasscode.passcode, NOT_VERIFIED,
            Some("Passcode does not exist")))
        Future.successful(Left(NotFound))
    }

  private def checkIfPasscodeIsStillAllowedToBeUsed(enteredEmailAndPasscode: EmailAndPasscode,
                                                    foundEmailPasscodeData: EmailAndPasscodeData, now: Long)
                                                   (implicit hc: HeaderCarrier) = {
    if (hasPasscodeExpired(foundEmailPasscodeData: EmailAndPasscodeData, now)) {
      metricsService.recordMetric("passcode_verification_success")
      auditService.sendExplicitAuditEvent(EmailVerificationCheck,
        VerificationCheckAuditEvent(enteredEmailAndPasscode.email, enteredEmailAndPasscode.passcode, NOT_VERIFIED,
          Some("Passcode expired")))
      Future.successful(Right(PasscodeExpired))
    } else {
      checkIfPasscodeMatches(enteredEmailAndPasscode, foundEmailPasscodeData)
    }
  }

  private def hasPasscodeExpired(foundEmailPasscodeData: EmailAndPasscodeData, currentTime: Long) = {
    val elapsedTimeInMilliseconds: Long = calculateElapsedTime(foundEmailPasscodeData.createdAt, currentTime)
    val allowedTimeGapForPasscodeUsageInMilliseconds: Long = Duration.ofMinutes(passcodeExpiry).toMillis
    elapsedTimeInMilliseconds > allowedTimeGapForPasscodeUsageInMilliseconds
  }

  private def checkIfPasscodeMatches(enteredEmailAndPasscode: EmailAndPasscode,
                                     maybeEmailAndPasscodeData: EmailAndPasscodeData)
                                    (implicit hc: HeaderCarrier) = {
    if (passcodeMatches(enteredEmailAndPasscode.passcode, maybeEmailAndPasscodeData.passcode)) {
      auditService.sendExplicitAuditEvent(EmailVerificationCheck,
        VerificationCheckAuditEvent(enteredEmailAndPasscode.email, enteredEmailAndPasscode.passcode, VERIFIED))
      Future.successful(Right(Verified))
    } else {

      auditService.sendExplicitAuditEvent(EmailVerificationCheck,
        VerificationCheckAuditEvent(enteredEmailAndPasscode.email, enteredEmailAndPasscode.passcode, NOT_VERIFIED,
          Some("Passcode mismatch")))
      Future.successful(Right(NotVerified))
    }
  }

  private def passcodeMatches(enteredPasscode: String, storedPasscode: String) = {
    enteredPasscode.equals(storedPasscode)
  }

  private def calculateElapsedTime(timeA: Long, timeB: Long): Long = {
    timeB - timeA
  }

  private def sendPasscode(data: EmailAndPasscodeData)
                          (implicit hc: HeaderCarrier) = {
    def success(response: HttpResponse) = {
      Right(PasscodeSent(response.json.as[GovUkNotificationId]))
    }

    def failure(response: HttpResponse) = {
      response.status match {
        case INTERNAL_SERVER_ERROR =>
          metricsService.recordMetric(s"UpstreamErrorResponse.${response.status}")
          logger.error(response.body)
          Left(GovNotifyServerError)
        case BAD_REQUEST =>
          logger.error(response.body)
          metricsService.recordMetric(s"UpstreamErrorResponse.${response.status}")
          Left(GovNotifyBadRequest)
        case FORBIDDEN =>
          logger.error(response.body)
          metricsService.recordMetric(s"UpstreamErrorResponse.${response.status}")
          Left(GovNotifyForbidden)
        case TOO_MANY_REQUESTS =>
          logger.error(response.body)
          metricsService.recordMetric(s"UpstreamErrorResponse.${response.status}")
          Left(GovNotifyTooManyRequests)
        case _ =>
          logger.error(response.body)
          metricsService.recordMetric(s"UpstreamErrorResponse.${response.status}")
          Left(GovNotifyServerError)
      }
    }

    govUkConnector.sendPasscode(data) transformWith {
      case Success(response) => response match {
        case _ if is2xx(response.status) => Future.successful(success(response))
        case _ => Future.successful(failure(response))
      }
      case Failure(response) =>
        metricsService.recordMetric(response.toString.trim.dropRight(1))
        metricsService.recordMetric("gov-notify_connection_failure")
        logger.error(response.getMessage)
        Future.successful(Left(GovNotifyServiceDown))
    }
  }

  private def persistAndSendPasscode(dataToSave: EmailAndPasscodeData)(implicit hc: HeaderCarrier) = {
    passcodeService.persistPasscode(dataToSave) transformWith {
      case Success(savedEmailPasscodeData) => sendPasscode(savedEmailPasscodeData)
      case Failure(err) => Future.successful(onDatabaseError(err))
    }
  }

  private def onDatabaseError(err: Throwable) = {
    err match {
      case _: MongoTimeoutException =>
        metricsService.recordMetric("mongo_cache_failure")
        logger.error(s"Database connection failed - ${err.getMessage}")
        Left(DatabaseServiceDown)
      case _ =>
        metricsService.recordMetric("mongo_cache_failure")
        logger.error(s"Database operation failed, ${err.getMessage}")
        Left(DatabaseServiceError)
    }
  }
}
