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
import com.github.mkroli.conquer.store.actions.{AddHeaderTransformation, EmitJson, Forward, ForwardJson, Log, TransformedAction}
import com.github.mkroli.conquer.store.{AttributeRule, Rule, Store}
import com.github.mkroli.conquer.{ApiCodecs, Settings}
import fs2.Stream
import fs2.kafka.{KafkaProducer, ProducerRecord, ProducerRecords, ProducerSettings}
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.StatusCode
import sttp.tapir.EndpointIO.Example
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.ServerEndpoint

case class RulesApi(settings: Settings, storeRef: Ref[IO, Store], producer: KafkaProducer.Metrics[IO, String, Option[String]]) extends ApiCodecs {
  private val ruleId = path[String]("rule")
    .example("rule1")

  private val rule = jsonBody[Rule]
    .example(Example.of(Rule(Set(AttributeRule("attr1", "test")), Forward("topic")), Some("Forward")))
    .example(Example.of(Rule(Set(AttributeRule("attr1", "test")), ForwardJson("topic")), Some("ForwardJson")))
    .example(Example.of(Rule(Set(AttributeRule("attr1", "test")), EmitJson("topic", Json.obj())), Some("EmitJson")))
    .example(Example.of(Rule(Set(AttributeRule("attr1", "test")), Log()), Some("Log")))
    .example(
      Example.of(
        Rule(Set(AttributeRule("attr1", "test")), TransformedAction(Forward("topic"), Seq(AddHeaderTransformation("header", "value")))),
        Some("ForwardWithTransformation")
      )
    )

  private def getAllRulesLogic(): IO[Either[Unit, Set[String]]] = {
    for {
      store <- storeRef.get
      rules = store.all().keySet
    } yield Right(rules)
  }

  private val getAllRulesEndpoint = endpoint
    .in("rules")
    .summary("List Rules")
    .description("List all Rules")
    .get
    .out(
      jsonBody[Set[String]]
        .example(Set("rule1", "rule2", "rule3"))
    )

  private def getRuleByIdLogic(id: String) = {
    for {
      store <- storeRef.get
      rule = store.get(id).toRight(StatusCode.NotFound)
    } yield rule
  }

  private val getRuleByIdEndpoint = endpoint
    .in("rules")
    .summary("Get Rule")
    .get
    .in(ruleId)
    .errorOut(
      statusCode
        .description(StatusCode.NotFound, "Rule doesn't exist")
    )
    .out(rule)

  private def storeRule(id: String, rule: Option[Rule]): IO[Either[Unit, Unit]] = {
    val ruleJson = rule.map(_.asJson.noSpaces)
    for {
      request <- producer.produce(ProducerRecords.one(ProducerRecord(settings.rulesTopic.name, id, ruleJson)))
      _       <- request
    } yield Right(())
  }

  private def setRuleByIdLogic(in: (String, Rule)): IO[Either[Unit, Unit]] = storeRule(in._1, Some(in._2))

  private val setRuleByIdEndpoint = endpoint
    .in("rules")
    .summary("Update Rule")
    .put
    .in(ruleId and rule)
    .out(statusCode(StatusCode.Accepted))

  private def deleteRuleByIdLogic(id: String): IO[Either[Unit, Unit]] = storeRule(id, None)

  private val deleteRuleByIdEndpoint = endpoint
    .in("rules")
    .summary("Delete Rule")
    .delete
    .in(ruleId)

  val endpoints: List[AnyEndpoint] = List(
    getAllRulesEndpoint,
    getRuleByIdEndpoint,
    setRuleByIdEndpoint,
    deleteRuleByIdEndpoint
  )

  val routes: List[ServerEndpoint[Fs2Streams[IO], IO]] = {
    List(
      getAllRulesEndpoint.serverLogic(_ => getAllRulesLogic()),
      getRuleByIdEndpoint.serverLogic(getRuleByIdLogic),
      setRuleByIdEndpoint.serverLogic(setRuleByIdLogic),
      deleteRuleByIdEndpoint.serverLogic(deleteRuleByIdLogic)
    )
  }
}

object RulesApi {
  def apply(settings: Settings, storeRef: Ref[IO, Store]): Stream[IO, RulesApi] = {
    val producerSettings = ProducerSettings[IO, String, Option[String]].withProperties(settings.kafka.removedAll(Settings.kafkaConsumerOnlySettings))
    val producer         = KafkaProducer.stream(producerSettings)
    producer.map(producer => RulesApi(settings, storeRef, producer))
  }
}
