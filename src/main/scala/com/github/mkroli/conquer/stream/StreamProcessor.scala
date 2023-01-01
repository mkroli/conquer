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

package com.github.mkroli.conquer.stream

import cats.effect._
import cats.implicits._
import com.github.mkroli.conquer.Settings
import com.github.mkroli.conquer.store.Store
import com.github.mkroli.conquer.store.actions.ActionContext
import com.github.mkroli.conquer.store.index.Record
import fs2.Stream
import fs2.kafka._
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.jdk.CollectionConverters._

case class StreamProcessor(settings: Settings, storeRef: Ref[IO, Store], deferred: Deferred[IO, Unit]) {
  val schemaRegistryClient = IO.blocking(new CachedSchemaRegistryClient(settings.schemaRegistry.url, 100))

  def consumer(implicit deserializer: Deserializer[IO, GenericRecord]) = KafkaConsumer.stream(
    ConsumerSettings[IO, Unit, GenericRecord]
      .withIsolationLevel(IsolationLevel.ReadCommitted)
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withProperties(settings.kafka.removedAll(Settings.kafkaProducerOnlySettings))
  )

  def producer = TransactionalKafkaProducer.stream(
    TransactionalProducerSettings(
      settings.stream.transactionalId,
      ProducerSettings[IO, Unit, Array[Byte]]
        .withProperties(settings.kafka.removedAll(Settings.kafkaConsumerOnlySettings))
    )
  )

  val genericRecordDeserializer = {
    for {
      schemaRegistry <- Stream.eval(schemaRegistryClient)
      deserializer   <- Stream.resource(Resource.fromAutoCloseable(IO.blocking(new KafkaAvroDeserializer(schemaRegistry))))
    } yield Deserializer.instance[IO, GenericRecord] { (topic, headers, data) =>
      for {
        d <- IO.blocking(deserializer.deserialize(topic, headers.asJava, data))
        r <- d match {
          case r: GenericRecord => IO.pure(r)
          case _                => IO.raiseError(new RuntimeException("Failed to decode record"))
        }
      } yield r
    }
  }

  def avroToRecord(record: GenericRecord): Record = {
    record.getSchema.getFields.asScala.toList
      .map { f => (f.name, f.schema().getType, record.get(f.name)) }
      .foldLeft(Record()) {
        case (r, (name, Schema.Type.STRING, value))                     => r + (name -> value.toString)
        case (r, (name, Schema.Type.INT, value: java.lang.Integer))     => r + (name -> BigDecimal(value))
        case (r, (name, Schema.Type.LONG, value: java.lang.Long))       => r + (name -> BigDecimal(value))
        case (r, (name, Schema.Type.BOOLEAN, value: java.lang.Boolean)) => r + (name -> value.booleanValue)
        case (r, _)                                                     => r
      }
  }

  def processRecord(ctx: ActionContext)(consumerRecord: ConsumerRecord[Unit, GenericRecord]): IO[List[ProducerRecord[Unit, Array[Byte]]]] = {
    val record = avroToRecord(consumerRecord.value)
    for {
      log   <- Slf4jLogger.create[IO]
      _     <- log.debug(s"Searching for record: ${record}")
      store <- storeRef.get
      actions <- store
        .find(record)
        .map {
          case (id, rule) => rule.action(ctx)(id, consumerRecord.value)
        }
        .toList
        .sequence
    } yield actions.flatten
  }

  def processTransaction(ctx: ActionContext)(committable: CommittableConsumerRecord[IO, Unit, GenericRecord]) = {
    processRecord(ctx)(committable.record)
      .map { records => CommittableProducerRecords(records, committable.offset) }
  }

  def stream = {
    for {
      log            <- Stream.eval(Slf4jLogger.create[IO])
      _              <- Stream.eval(deferred.get)
      _              <- Stream.eval(log.info("Starting stream"))
      deserializer   <- genericRecordDeserializer
      schemaRegistry <- Stream.eval(schemaRegistryClient)
      ctx = ActionContext(settings, schemaRegistry)
      producer <- producer
      consumer <- consumer(deserializer)
      _        <- Stream.eval(consumer.subscribeTo(settings.inputTopic.name))
      record <- consumer.stream
        .mapAsync(32)(processTransaction(ctx))
        .groupWithin(settings.stream.intervalRecords, settings.stream.intervalDuration)
        .map(TransactionalProducerRecords(_))
        .evalMap(producer.produce)
    } yield record
  }
}
