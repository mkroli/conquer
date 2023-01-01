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

package com.github.mkroli.conquer.verification

import cats.effect._
import com.github.mkroli.conquer.ApiCodecs
import com.github.mkroli.conquer.store.Store
import com.github.mkroli.conquer.store.actions.{Action, Log}
import com.github.mkroli.conquer.store.index.Record
import io.circe.generic.auto._
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.EndpointIO.Example
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.ServerEndpoint

case class VerificationApi(storeRef: Ref[IO, Store]) extends ApiCodecs {
  private def verifyLogic(record: Record): IO[Either[Unit, Map[String, Action]]] = {
    for {
      store <- storeRef.get
      result  = store.find(record)
      actions = result.view.mapValues(_.action).to(Map)
    } yield Right(actions)
  }

  private val verifyEndpoint: PublicEndpoint[Record, Unit, Map[String, Action], Any] = endpoint
    .in("verify")
    .summary("Rule Verification")
    .description("Rule Verification")
    .post
    .in(
      jsonBody[Record]
        .example(Example.of(Record("name" -> "test")))
    )
    .out(
      jsonBody[Map[String, Action]]
        .example(Example.of(Map("Rule 1" -> Log())))
    )

  val endpoints: List[AnyEndpoint] = List(verifyEndpoint)

  val routes: List[ServerEndpoint[Fs2Streams[IO], IO]] = List(verifyEndpoint.serverLogic(verifyLogic))
}
