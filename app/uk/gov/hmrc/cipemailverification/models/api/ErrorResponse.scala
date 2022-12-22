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

package uk.gov.hmrc.cipemailverification.models.api

import play.api.libs.json.{Json, OWrites}
import uk.gov.hmrc.cipemailverification.models.api.ErrorResponse.Codes.Code
import uk.gov.hmrc.cipemailverification.models.api.ErrorResponse.Messages.Message

case class ErrorResponse(code: Code, message: Message)

object ErrorResponse {
  implicit val writes: OWrites[ErrorResponse] = Json.writes[ErrorResponse]

  object Codes extends Enumeration {
    type Code = Int

    val VALIDATION_ERROR = 1002
    val EXTERNAL_SERVER_FAIL_VALIDATION = 1004
    val PASSCODE_CHECK_FAIL = 1005
    val EXTERNAL_SERVER_UNREACHABLE = 1006
    val MESSAGE_THROTTLED_OUT = 1007
    val PASSCODE_PERSISTING_FAIL = 1008
    val SERVER_UNREACHABLE = 1009
    val EXTERNAL_SERVER_FAIL_FORBIDDEN = 1010
    val SERVER_ERROR = 1011
    val EXTERNAL_SERVER_ERROR = 1012
    val PASSCODE_ENTERED_EXPIRED = 1013
    val PASSCODE_NOT_FOUND = 1014
    val NOTIFICATION_NOT_FOUND = 1015
    val REQUEST_STILL_PROCESSING = 1016
  }

  object Messages extends Enumeration {
    type Message = String

    val ENTER_A_VALID_EMAIL = "Enter a valid email"
    val SERVER_CURRENTLY_UNAVAILABLE = "Server currently unavailable"
    val SERVER_EXPERIENCED_AN_ISSUE = "Server has experienced an issue"
    val EXTERNAL_SERVER_EXPERIENCED_AN_ISSUE = "External server has experienced an issue"
    val EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE = "External server currently unavailable"
    val PASSCODE_ALLOWED_TIME_ELAPSED = "The passcode has expired. Request a new passcode"
    val ENTER_A_CORRECT_PASSCODE = "Enter a correct passcode"
    val ENTER_A_VALID_NOTIFICATION_ID = "Enter a valid notification Id"
    val NOTIFICATION_ID_NOT_FOUND = "Notification Id not found"
    val THROTTLED_TOO_MANY_REQUESTS = "The request for the API is throttled as you have exceeded your quota"
    val REQUEST_IN_PROGRESS = "The request is still being processed"
  }
}
