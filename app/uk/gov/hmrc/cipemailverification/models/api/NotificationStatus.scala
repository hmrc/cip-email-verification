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

package uk.gov.hmrc.cipemailverification.models.api

import play.api.libs.json.{Json, OWrites}
import uk.gov.hmrc.cipemailverification.models.api.NotificationStatus.Messages.Message
import uk.gov.hmrc.cipemailverification.models.api.NotificationStatus.Statuses.Status

case class NotificationStatus(notificationStatus: Status, message: Message)

object NotificationStatus {
  implicit val writes: OWrites[NotificationStatus] = Json.writes[NotificationStatus]

  object Statuses extends Enumeration {
    type Status = Value

    val CREATED,
    SENDING,
    PENDING,
    SENT,
    DELIVERED,
    PERMANENT_FAILURE,
    TEMPORARY_FAILURE,
    TECHNICAL_FAILURE = Value
  }

  object Messages extends Enumeration {
    type Message = String

    val CREATED = "Message is in the process of being sent"
    val SENDING = "Message has been sent"
    val PENDING = "Message is in the process of being delivered"
    val SENT = "Message was sent successfully"
    val DELIVERED = "Message was delivered successfully"
    val PERMANENT_FAILURE = "Message was unable to be delivered by the network provider"
    val TEMPORARY_FAILURE = "Message was unable to be delivered by the network provider"
    val TECHNICAL_FAILURE = "There is a problem with the notification vendor"
  }
}
