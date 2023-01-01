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

import com.github.mkroli.conquer.store.index._

case class Store private[store] (rules: Map[String, Rule], tree: Node[(String, Rule)], knownKeysOrder: Map[String, Int]) {
  private def sortedAttributes(rule: Rule) = rule.attributes.toList.sortWith { (a, b) =>
    (knownKeysOrder.get(a.key), knownKeysOrder.get(b.key)) match {
      case (Some(a), Some(b)) => a < b
      case (Some(_), None)    => true
      case (None, Some(_))    => false
      case (None, None)       => a.key.compareTo(b.key) < 0
    }
  }

  private def branch(id: String, rule: Rule): Node[(String, Rule)] = {
    def branch(attributes: List[(String, Attribute)]): Node[(String, Rule)] = attributes match {
      case Nil               => Node() ~> Leaf((id, rule))
      case (key, id) :: tail => Node() ~> (key, id, branch(tail))
    }
    branch(sortedAttributes(rule).map(r => (r.key, r.id)))
  }

  def put(id: String, rule: Rule): Store = delete(id).copy(
    rules = rules + (id -> rule),
    tree = tree merged branch(id, rule)
  )

  def delete(id: String): Store = {
    rules
      .get(id)
      .map { rule =>
        copy(
          rules = rules - id,
          tree = tree delete branch(id, rule)
        )
      }
      .getOrElse(this)
  }

  def get(id: String): Option[Rule] = rules.get(id)

  def all(): Map[String, Rule] = rules

  def find(record: Record): Map[String, Rule] = tree.find(record).map(_.value).toMap

  def size: Int = tree.size

  def depth: Int = tree.depth
}

object Store {
  def apply(knownKeysOrder: List[String]): Store = Store(Map.empty, Node(), knownKeysOrder.zipWithIndex.toMap)
}
