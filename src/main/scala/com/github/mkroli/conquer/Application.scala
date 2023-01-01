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

import cats.effect._
import cats.effect.std.Dispatcher
import com.github.mkroli.conquer.metrics.Metrics
import com.github.mkroli.conquer.store._
import com.github.mkroli.conquer.store.index.RulesLoader
import com.github.mkroli.conquer.stream.StreamProcessor

object Application extends IOApp.Simple {
  override def run: IO[Unit] = Dispatcher.sequential[IO].use { dispatcher =>
    for {
      config   <- Settings.load
      metrics  <- Metrics()
      store    <- Ref[IO].of(Store(config.index.attributesOrder))
      _        <- metrics.bindStoreMetrics(store)(dispatcher)
      _        <- KafkaTopics(config).create
      deferred <- Deferred[IO, Unit]
      rulesLoader     = RulesLoader(config, metrics, store, deferred).stream
      apiStream       = Api(config, metrics, store, deferred).stream
      streamProcessor = StreamProcessor(config, store, deferred).stream
      streams         = apiStream concurrently rulesLoader concurrently streamProcessor
      result <- streams.compile.drain
    } yield result
  }
}
