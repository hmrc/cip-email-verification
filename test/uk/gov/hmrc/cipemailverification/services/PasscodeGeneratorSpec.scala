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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PasscodeGeneratorSpec extends AnyWordSpec
  with Matchers {

  private val passcodeGenerator = new PasscodeGenerator()

  "create 6 digit passcode" in {
    passcodeGenerator.passcodeGenerator().forall(y => y.isUpper) shouldBe true
    passcodeGenerator.passcodeGenerator().forall(y => y.isLetter) shouldBe true

    val illegalChars = List('@', '£', '$', '%', '^', '&', '*', '(', ')', '-', '+')
    passcodeGenerator.passcodeGenerator().toList map (y => assertResult(illegalChars contains y)(false))

    passcodeGenerator.passcodeGenerator().length shouldBe 6
  }
}
