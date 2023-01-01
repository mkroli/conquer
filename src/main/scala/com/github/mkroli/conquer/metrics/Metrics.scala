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
import io.micrometer.core.instrument.binder.jvm._
import io.micrometer.core.instrument.binder.system.{DiskSpaceMetrics, FileDescriptorMetrics, ProcessorMetrics, UptimeMetrics}
import io.micrometer.prometheus.{PrometheusConfig, PrometheusMeterRegistry}
import sttp.tapir.server.interceptor._

import java.io.File

case class Metrics(registry: PrometheusMeterRegistry) {
  def bindStoreMetrics(store: Ref[IO, Store])(dispatcher: Dispatcher[IO]) = {
    StoreMetrics(store).bindTo(registry)(dispatcher)
  }

  def metricsInterceptor: Interceptor[IO] = MetricsInterceptor(registry)
}

object Metrics {
  private def registerSystemMetrics(registry: PrometheusMeterRegistry): IO[Unit] = IO {
    new UptimeMetrics().bindTo(registry)
    new ProcessorMetrics().bindTo(registry)
    File
      .listRoots()
      .map(new DiskSpaceMetrics(_))
      .foreach(_.bindTo(registry))
    new FileDescriptorMetrics().bindTo(registry)
  }

  private def registerJvmMetrics(registry: PrometheusMeterRegistry): IO[Unit] = IO {
    new JvmGcMetrics().bindTo(registry)
    new JvmInfoMetrics().bindTo(registry)
    new JvmMemoryMetrics().bindTo(registry)
    new ClassLoaderMetrics().bindTo(registry)
    new JvmCompilationMetrics().bindTo(registry)
    new JvmHeapPressureMetrics().bindTo(registry)
    new JvmThreadMetrics().bindTo(registry)
  }

  def apply(): IO[Metrics] = {
    for {
      registry <- IO(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT))
      _        <- registerSystemMetrics(registry)
      _        <- registerJvmMetrics(registry)
    } yield Metrics(registry)
  }
}
