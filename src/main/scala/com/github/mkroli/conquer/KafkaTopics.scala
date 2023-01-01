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

import cats.Foldable
import cats.effect._
import fs2.kafka._
import org.apache.kafka.clients.admin.{AlterConfigOp, ConfigEntry, NewTopic}
import org.apache.kafka.common.config.ConfigResource
import org.typelevel.log4cats.slf4j.Slf4jLogger

case class KafkaTopics(settings: Settings) {
  private def topic(name: String) = new ConfigResource(ConfigResource.Type.TOPIC, name)

  private def config(settings: (String, String)*): List[AlterConfigOp] = settings.toList.map {
    case (key, value) => new AlterConfigOp(new ConfigEntry(key, value), AlterConfigOp.OpType.SET)
  }

  private implicit class SimpleAdminClient(admin: KafkaAdminClient[IO]) {
    def alterConfig[G[_]: Foldable](resources: (ConfigResource, G[AlterConfigOp])*): IO[Unit] = {
      val a = resources.toMap
      admin.alterConfigs(a)
    }

    def createTopicIfNotExists(topic: NewTopic): IO[Boolean] = admin.createTopic(topic).as(true).handleError(_ => false)
  }

  private def newTopic(t: TopicSettings) = new NewTopic(t.name, t.partitions, t.replication)

  def create: IO[Unit] = {
    val admin = KafkaAdminClient.resource[IO](AdminClientSettings("").withProperties(settings.kafka.removedAll(Settings.kafkaConsumerOnlySettings)))

    admin.use { admin =>
      def createTopic(topic: NewTopic): IO[Boolean] = {
        for {
          created <- admin.createTopicIfNotExists(topic)
          log     <- Slf4jLogger.create[IO]
          _       <- log.info(if (created) s"Topic ${topic.name()} created" else s"Topic ${topic.name()} existing")
        } yield created
      }

      for {
        log <- Slf4jLogger.create[IO]
        _   <- createTopic(newTopic(settings.rulesTopic))
        _ <- admin.alterConfig(
          topic(settings.rulesTopic.name) -> config(
            "cleanup.policy"            -> "compact",
            "delete.retention.ms"       -> "1000",
            "max.compaction.lag.ms"     -> "1000",
            "min.cleanable.dirty.ratio" -> "0",
            "segment.bytes"             -> "1048576",
            "segment.ms"                -> "60000"
          )
        )
        _ <- log.info(s"Topic ${settings.rulesTopic.name} configured")
        _ <- createTopic(newTopic(settings.inputTopic))
        _ <- createTopic(newTopic(settings.outputTopic))
      } yield ()
    }
  }
}
