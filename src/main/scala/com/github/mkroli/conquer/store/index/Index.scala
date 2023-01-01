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

package com.github.mkroli.conquer.store.index

sealed trait TreeSearch[T] {
  def find(record: Record): Set[Leaf[T]]
}

case class Leaf[T](value: T) extends TreeSearch[T] {
  override def find(record: Record): Set[Leaf[T]] = Set(this)
}

case class Node[T] private (successorNodes: Map[(String, Attribute), Node[T]], leaves: Set[Leaf[T]]) extends TreeSearch[T] {
  def this() = this(Map.empty, Set.empty)

  def merged(node: Node[T]): Node[T] = copy(
    successorNodes = {
      val successorKeys = this.successorNodes.keySet ++ node.successorNodes.keySet
      successorKeys.map { key =>
        val a = this.successorNodes.get(key)
        val b = node.successorNodes.get(key)
        key -> List(a, b).flatten.reduce(_ merged _)
      }.toMap
    },
    leaves = this.leaves ++ node.leaves
  )

  def delete(node: Node[T]): Node[T] = copy(
    successorNodes = {
      successorNodes.flatMap {
        case (key, a) =>
          val b = node.successorNodes
            .get(key)
            .map(a.delete)
            .getOrElse(a)
          if (b.leaves.isEmpty && b.successorNodes.isEmpty)
            None
          else
            Some((key, b))
      }
    },
    leaves = this.leaves -- node.leaves
  )

  def ~>(key: String, id: Attribute, node: Node[T]): Node[T] = {
    copy(successorNodes = successorNodes.updatedWith((key, id)) {
      case Some(child) => Some(node merged child)
      case None        => Some(node)
    })
  }

  def ~>(leaf: Leaf[T]): Node[T] = copy(leaves = leaves + leaf)

  override def find(record: Record): Set[Leaf[T]] = {
    val filteredSuccessorNodes       = record.flatMap(successorNodes.get)
    val filteredSuccessorNodeActions = filteredSuccessorNodes.flatMap(_.find(record))
    leaves ++ filteredSuccessorNodeActions
  }

  def depth: Int = {
    if (successorNodes.isEmpty) {
      0
    } else {
      1 + successorNodes.values.map(_.depth).max
    }
  }

  def size: Int = {
    1 + leaves.size + successorNodes.values.map(_.size).sum
  }
}

object Node {
  def apply[T](): Node[T] = new Node[T]
}
