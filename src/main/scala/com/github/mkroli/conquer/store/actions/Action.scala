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

package com.github.mkroli.conquer.store.actions

import cats.effect.IO
import fs2.kafka.{Headers, ProducerRecord, Serializer}
import io.circe.Json
import org.apache.avro.generic.GenericRecord
import org.typelevel.log4cats.slf4j.Slf4jLogger

sealed trait Action {
  def apply(ctx: ActionContext)(ruleId: String, record: GenericRecord): IO[Option[ProducerRecord[Unit, Array[Byte]]]]
}

sealed trait ForwardingAction extends Action {
  val topic: String

  def serializer(ctx: ActionContext): Serializer[IO, GenericRecord]

  override def apply(ctx: ActionContext)(ruleId: String, record: GenericRecord): IO[Option[ProducerRecord[Unit, Array[Byte]]]] = {
    serializer(ctx)
      .serialize(topic, Headers.empty, record)
      .map(ProducerRecord(topic, (), _))
      .map(Some(_))
  }
}

case class Forward(topic: String) extends ForwardingAction {
  override def serializer(ctx: ActionContext) = ctx.avroSerializer
}

case class ForwardJson(topic: String) extends ForwardingAction {
  override def serializer(ctx: ActionContext) = ctx.jsonSerializer
}

case class EmitJson(topic: String, payload: Json) extends Action {
  override def apply(ctx: ActionContext)(ruleId: String, record: GenericRecord): IO[Option[ProducerRecord[Unit, Array[Byte]]]] = {
    IO.pure(Some(ProducerRecord(topic, (), payload.noSpaces.getBytes)))
  }
}

case class Log() extends Action {
  override def apply(ctx: ActionContext)(ruleId: String, record: GenericRecord): IO[Option[ProducerRecord[Unit, Array[Byte]]]] = {
    for {
      log <- Slf4jLogger.create[IO]
      _   <- log.debug(s"${ruleId}: ${record.toString}")
    } yield None
  }
}

case class TransformedAction(action: Action, transformations: Seq[Transformation]) extends Action {
  override def apply(ctx: ActionContext)(ruleId: String, record: GenericRecord): IO[Option[ProducerRecord[Unit, Array[Byte]]]] = {
    for {
      response <- action(ctx)(ruleId, record)
    } yield response
  }
}
