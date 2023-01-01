/*
 * Copyright 2023 Michael Krolikowski
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

package com.github.mkroli.conquer.store

import com.github.mkroli.conquer.store.index.{Attribute, Record}
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.scalacheck.all._
import io.circe.Json
import org.scalacheck.Arbitrary._
import org.scalacheck.ScalacheckShapeless._
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class StoreSpec extends AnyFreeSpec with ScalaCheckPropertyChecks {
  implicit val arbitraryAttributeRule = Arbitrary(
    for {
      key <- arbitrary[String Refined NonEmpty]
      id  <- arbitrary[Attribute]
    } yield AttributeRule(key.value, id)
  )

  implicit val arbitraryJson: Arbitrary[Json] = Arbitrary(
    Gen.const(Json.Null)
  )

  def matchingRecord(rule: Rule): Record = Record(rule.attributes.map(r => (r.key, r.id)).toSeq: _*)

  "ConQuer Store" - {
    "when empty" - {
      "should get any inserted Rule by ID" in forAll { (id: String, rule: Rule) =>
        val store = Store(Nil).put(id, rule)
        assert(store.get(id) === Some(rule))
      }

      "should not get a Rule if the IDs don't equal" in forAll { (id: String, rule: Rule, idMismatch: String) =>
        whenever(id != idMismatch) {
          val store = Store(Nil).put(id, rule)
          assert(store.get(idMismatch) === None)
        }
      }

      "should find any inserted Rule given the exact pattern" in forAll { (id: String, rule: Rule) =>
        val store  = Store(Nil).put(id, rule)
        val record = matchingRecord(rule)
        assert(store.find(record) === Map(id -> rule))
      }

      "should find any matching non-exhaustive Rule" in forAll { (id: String, rule: Rule, additionalAttributes: Record) =>
        whenever(matchingRecord(rule).keySet.intersect(additionalAttributes.keySet).isEmpty) {
          val store  = Store(Nil).put(id, rule)
          val record = matchingRecord(rule) ++ additionalAttributes
          assert(store.find(record) === Map(id -> rule))
        }
      }

      "should not find a Rule if it should match an attribute which doesn't exist" in forAll { (id: String, rule: Rule, additionalAttribute: AttributeRule) =>
        whenever(!rule.attributes.contains(additionalAttribute)) {
          val store  = Store(Nil).put(id, rule.copy(attributes = rule.attributes + additionalAttribute))
          val record = matchingRecord(rule)
          assert(store.find(record) === Map.empty)
        }
      }

      "should not find a Rule if any single attribute doesn't match" in forAll { (id: String, rule: Rule, ar: AttributeRule, mismatch: Attribute) =>
        whenever(ar.id != mismatch) {
          val store  = Store(Nil).put(id, rule.copy(attributes = rule.attributes + ar))
          val record = matchingRecord(rule) + (ar.key -> mismatch)
          assert(store.find(record) === Map.empty)
        }
      }

      "should find only Rules matching if multiple exist" in forAll { (rules: Set[(String, Rule)]) =>
        val store = rules.foldLeft(Store(Nil)) {
          case (store, (id, rule)) => store.put(id, rule)
        }
        rules.foreach {
          case (id, rule) =>
            val record  = matchingRecord(rule)
            val results = store.find(record)
            assert(results.get(id) === Some(rule))
            whenever(results.size > 1) {
              results.foreach {
                case (_, rule) => rule.attributes.foreach { attributeRule => assert(record.get(attributeRule.key) === Some(attributeRule.id)) }
              }
            }
        }
      }
    }
  }
}
