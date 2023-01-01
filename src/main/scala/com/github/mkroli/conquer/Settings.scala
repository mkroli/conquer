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

package com.github.mkroli.conquer

import cats.effect.{IO, Resource}
import com.comcast.ip4s.{Hostname, Port}
import org.typelevel.log4cats.slf4j.Slf4jLogger
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import pureconfig.module.ip4s._
import pureconfig.module.catseffect.syntax.CatsEffectConfigSource
import pureconfig.module.yaml.YamlConfigSource

import scala.concurrent.duration.FiniteDuration
import scala.io.Source

case class HttpSettings(
    bindHost: Hostname,
    bindPort: Port
)

case class TopicSettings(
    name: String,
    partitions: Int,
    replication: Short
)

case class StreamSettings(
    transactionalId: String,
    intervalDuration: FiniteDuration,
    intervalRecords: Int
)

case class IndexSettings(
    attributesOrder: List[String]
)

case class SchemaRegistrySettings(
    url: String
)

case class Settings(
    index: IndexSettings,
    http: HttpSettings,
    stream: StreamSettings,
    rulesTopic: TopicSettings,
    inputTopic: TopicSettings,
    outputTopic: TopicSettings,
    schemaRegistry: SchemaRegistrySettings,
    kafka: Map[String, String]
)

object Settings {
  val kafkaProducerOnlySettings = Set("retries")
  val kafkaConsumerOnlySettings = Set("group.id")

  val load: IO[Settings] = {
    val config = Resource
      .fromAutoCloseable(IO.blocking(Source.fromResource("conquer.yaml")))
      .map(_.mkString)
      .map(YamlConfigSource.string)
      .map(_.asObjectSource)
      .map(_.withFallback(ConfigSource.default))
      .use(_.loadF[IO, Settings]())
    for {
      logger <- Slf4jLogger.create[IO]
      config <- config
      _      <- logger.info(s"Configuration: $config")
    } yield config
  }
}
