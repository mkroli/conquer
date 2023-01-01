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

package com.github.mkroli.conquer.metrics

import cats.effect.std.Dispatcher
import cats.effect.{IO, Ref}
import com.github.mkroli.conquer.store.Store
import io.micrometer.core.instrument.{Gauge, MeterRegistry}

case class StoreMetrics(store: Ref[IO, Store]) {
  val storeSize = store.get.map[Number](_.all().size)

  def bindTo(registry: MeterRegistry)(dispatcher: Dispatcher[IO]) = IO[Unit] {
    Gauge
      .builder(
        "rules",
        () => dispatcher.unsafeRunSync(storeSize)
      )
      .description("Number of rules currently loaded")
      .register(registry)
    ()
  }
}
