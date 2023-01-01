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

package com.github.mkroli.conquer.samples

import cats.effect.{ExitCode, IO, IOApp}
import fs2._
import fs2.kafka.vulcan.{AvroSettings, SchemaRegistryClientSettings, avroSerializer}
import fs2.kafka.{KafkaProducer, ProducerRecord, ProducerRecords, ProducerSettings}
import vulcan.Codec
import vulcan.generic._

import scala.concurrent.duration.DurationInt

case class Event(
    user: String,
    `type`: String,
    level: Int,
    message: String
)

object SampleProducer extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    implicit val eventCodec = Codec.derive[Event]

    val avroSettings =
      AvroSettings {
        SchemaRegistryClientSettings[IO]("http://localhost:8081")
      }

    implicit val eventSerializer = avroSerializer[Event].using(avroSettings)

    val producerSettings =
      ProducerSettings[IO, Unit, Event]
        .withBootstrapServers("localhost:9092")

    KafkaProducer
      .stream(producerSettings)
      .flatMap { producer =>
        val events = Stream(Event("user1", "example1", 1, "Example Message 1")).repeat
        Stream
          .awakeEvery[IO](1.second)
          .zipRight(events)
          .map(ProducerRecord("events", (), _))
          .map(ProducerRecords.one(_))
          .evalTap(producer.produce)
      }
      .compile
      .drain
      .as(ExitCode.Success)
  }
}
