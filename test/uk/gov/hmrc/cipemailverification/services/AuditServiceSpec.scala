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

import org.mockito.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{Json, OWrites}
import uk.gov.hmrc.cipemailverification.models.domain.audit.AuditType.EmailVerificationRequest
import uk.gov.hmrc.cipemailverification.models.domain.audit.VerificationRequestAuditEvent
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.ExecutionContext

class AuditServiceSpec extends AnyWordSpec
  with Matchers
  with IdiomaticMockito {

  "sendEvent" should {
    "send AuditEvent to audit service" in new SetUp {
      private val auditEvent = VerificationRequestAuditEvent("myEmail", "myPasscode")
      service.sendExplicitAuditEvent(EmailVerificationRequest, auditEvent)

      mockAuditConnector
        .sendExplicitAudit[VerificationRequestAuditEvent](EmailVerificationRequest.toString, auditEvent) was called
    }
  }

  trait SetUp {
    implicit val writes: OWrites[VerificationRequestAuditEvent] = Json.writes[VerificationRequestAuditEvent]
    implicit val ex: ExecutionContext = ExecutionContext.global
    implicit val hc: HeaderCarrier = HeaderCarrier()
    protected val mockAuditConnector: AuditConnector = mock[AuditConnector]
    protected val service: AuditService = new AuditService(mockAuditConnector)
  }
}
