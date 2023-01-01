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

import com.github.mkroli.conquer.store.actions.Log
import com.github.mkroli.conquer.store.index.StringAttribute
import org.scalameter.api._
import org.scalameter.picklers.Implicits._

object StoreBenchmark extends Bench.OfflineReport {
  val attributes = Gen
    .single("attributes")(20)
    .map { count => (1 to count).map { i => (offset: Int) => AttributeRule(i.toString, StringAttribute((offset + i).toString)) }.toSet }

  val rules = Gen
    .range("rules")(200, 2000, 200)
    .cross(attributes)
    .map {
      case (count, attributes) =>
        (1 to count).iterator.map(i => Rule(attributes.map(_(i)), Log()))
    }

  val store = rules.map { rules => rules.zipWithIndex.foldLeft(Store(Nil)) { case (store, (rule, i)) => store.put(i.toString, rule) } }

  val record = Gen
    .single("record")(20)
    .map { count => (1 to count).map { i => i.toString -> StringAttribute((i + 1).toString) }.toMap }

  performance of "Store" in {
    measure method "put" in {
      using(rules) in { rules =>
        val _ = rules.zipWithIndex.foldLeft(Store(Nil)) {
          case (store, (rule, i)) =>
            store.put(i.toString, rule)
        }
      }
    }

    measure method "find" in {
      using(store cross record) in {
        case (store, record) =>
          assert(store.find(record).nonEmpty)
      }
    }
  }
}
