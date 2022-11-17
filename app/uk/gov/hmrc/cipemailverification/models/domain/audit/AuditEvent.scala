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

package uk.gov.hmrc.cipemailverification.models.domain.audit

import play.api.libs.json.{Json, OWrites}

sealed class AuditEvent(email: String, passcode: String)

sealed case class VerificationCheckAuditEvent(email: String, passcode: String, result: String,
                                              failureReason: Option[String] = None) extends AuditEvent(email, passcode)

object VerificationCheckAuditEvent {
  implicit val writes: OWrites[VerificationCheckAuditEvent] = Json.writes[VerificationCheckAuditEvent]
}

sealed case class VerificationDeliveryResultRequestAuditEvent(email: String, passcode: String, notificationId: String,
                                                              notificationStatus: String) extends AuditEvent(email, passcode)

object VerificationDeliveryResultRequestAuditEvent {
  implicit val writes: OWrites[VerificationDeliveryResultRequestAuditEvent] =
    Json.writes[VerificationDeliveryResultRequestAuditEvent]
}

sealed case class VerificationRequestAuditEvent(email: String, passcode: String) extends AuditEvent(email, passcode)

object VerificationRequestAuditEvent {
  implicit val writes: OWrites[VerificationRequestAuditEvent] = Json.writes[VerificationRequestAuditEvent]
}
