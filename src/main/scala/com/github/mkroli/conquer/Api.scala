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
import com.github.mkroli.conquer.metrics.{Metrics, MetricsApi}
import com.github.mkroli.conquer.store.Store
import com.github.mkroli.conquer.store.index.RulesApi
import com.github.mkroli.conquer.verification.VerificationApi
import fs2.Stream
import org.http4s.ember.server.EmberServerBuilder
import org.typelevel.log4cats.slf4j.Slf4jLogger
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import sttp.tapir.swagger.bundle.SwaggerInterpreter

case class Api(
    settings: Settings,
    metrics: Metrics,
    store: Ref[IO, Store],
    deferred: Deferred[IO, Unit]
) {
  def stream = {
    for {
      log <- Stream.eval(Slf4jLogger.create[IO])
      _   <- Stream.eval(deferred.get >> log.info("Starting API"))
      metricsApi      = MetricsApi(metrics.registry)
      verificationApi = VerificationApi(store)
      rulesApi <- RulesApi(settings, store)
      openApi = SwaggerInterpreter()
        .fromEndpoints[IO](metricsApi.endpoints ::: verificationApi.endpoints ::: rulesApi.endpoints, BuildInfo.name, BuildInfo.version)
      serverOptions = Http4sServerOptions.default[IO].prependInterceptor(metrics.metricsInterceptor)
      routes = Http4sServerInterpreter[IO](serverOptions)
        .toRoutes(openApi ::: metricsApi.routes ::: verificationApi.routes ::: rulesApi.routes)
      serverResource = EmberServerBuilder
        .default[IO]
        .withHost(settings.http.bindHost)
        .withPort(settings.http.bindPort)
        .withHttpApp(routes.orNotFound)
        .build
        .use(_ => IO.never.as(()))
      server <- Stream.eval(serverResource)
    } yield server
  }
}
