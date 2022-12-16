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

package uk.gov.hmrc.cipemailverification.models.domain.result

sealed trait ApplicationError

case object ValidationServiceDown extends ApplicationError

case object ValidationError extends ApplicationError

case object ValidationServiceError extends ApplicationError

case object DatabaseServiceDown extends ApplicationError

case object GovNotifyBadRequest extends ApplicationError

case object GovNotifyForbidden extends ApplicationError

case object GovNotifyServerError extends ApplicationError

case object GovNotifyServiceDown extends ApplicationError

case object GovNotifyTooManyRequests extends ApplicationError

case object RequestInProgress extends ApplicationError

case object NotFound extends ApplicationError
