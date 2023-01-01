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

import cats.effect._
import com.github.mkroli.conquer.metrics.{IOMetrics, Metrics}
import com.github.mkroli.conquer.store.{Rule, Store}
import com.github.mkroli.conquer.{ApiCodecs, Settings}
import fs2.kafka.{AutoOffsetReset, ConsumerRecord, ConsumerSettings, KafkaConsumer}
import io.circe.generic.auto._
import io.circe.parser._
import io.micrometer.core.instrument.Timer
import org.apache.kafka.common.{PartitionInfo, TopicPartition}
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.Duration

case class RulesLoader(
    settings: Settings,
    metrics: Metrics,
    storeRef: Ref[IO, Store],
    deferred: Deferred[IO, Unit]
) extends ApiCodecs {
  private val loadingFinished = {
    for {
      log      <- Slf4jLogger.create[IO]
      finished <- deferred.complete(())
      _        <- if (finished) log.info("Finished loading rules") else IO.unit
    } yield finished
  }

  private val timer = IO {
    Timer
      .builder("store")
      .maximumExpectedValue(Duration.ofMillis(1000))
      .publishPercentileHistogram()
      .register(metrics.registry)
  }

  private def update(record: ConsumerRecord[String, Option[String]]): IO[Unit] = {
    for {
      log   <- Slf4jLogger.create[IO]
      timer <- timer
      _ <- record.value match {
        case None =>
          for {
            _ <- storeRef.update(_.delete(record.key)).record(timer)
            _ <- log.debug(s"Rule ${record.key} removed")
          } yield ()
        case Some(value) =>
          decode[Rule](value) match {
            case Left(err) =>
              for {
                _ <- log.warn(err)("Failed to parse rule")
              } yield ()
            case Right(rule) =>
              for {
                _ <- storeRef.update(_.put(record.key, rule)).record(timer)
                _ <- log.debug(s"Rule ${record.key} loaded")
              } yield ()
          }
      }
    } yield ()
  }

  def stream = {
    val consumerSettings = ConsumerSettings[IO, String, Option[String]]
      .withProperties(settings.kafka.removedAll(Settings.kafkaProducerOnlySettings))
      .withAutoOffsetReset(AutoOffsetReset.Earliest)

    def topicPartition(p: PartitionInfo) = new TopicPartition(p.topic(), p.partition())

    KafkaConsumer
      .stream(consumerSettings)
      .evalMap { consumer =>
        for {
          _          <- consumer.assign(settings.rulesTopic.name)
          partitions <- consumer.partitionsFor(settings.rulesTopic.name)
          offsets    <- consumer.endOffsets(partitions.map(topicPartition).toSet)
        } yield (consumer, offsets)
      }
      .evalTap {
        case (_, offsets) if offsets.filter(_._2 > 1).isEmpty => loadingFinished
        case _                                                => IO.unit
      }
      .flatMap {
        case (s, offsets) =>
          s.stream
            .map(_.record)
            .zipWithScan1(offsets) {
              case (offsets, _) if offsets.isEmpty => offsets
              case (offsets, record) =>
                val topicPartition = new TopicPartition(record.topic, record.partition)
                val partitionConsumed = offsets
                  .get(topicPartition)
                  .map(_ - record.offset)
                  .map(_ <= 1)
                  .getOrElse(false)
                if (partitionConsumed) {
                  offsets - topicPartition
                } else {
                  offsets
                }
            }
            .evalTap {
              case (record, _) => update(record)
            }
            .evalTap {
              case (_, offsets) if offsets.isEmpty => loadingFinished
              case _                               => IO.unit
            }
      }
  }
}
