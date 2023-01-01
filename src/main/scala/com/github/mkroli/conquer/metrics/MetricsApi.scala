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

import cats.effect.IO
import io.micrometer.core.instrument.Gauge
import io.micrometer.prometheus.{PrometheusConfig, PrometheusMeterRegistry}
import io.prometheus.client.exporter.common.TextFormat
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.MediaType
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint

case class MetricsApi private (registry: PrometheusMeterRegistry) {
  private def metricsCodec(format: String) =
    Codec.string
      .mapDecode[PrometheusMeterRegistry](s => DecodeResult.Error(s, new RuntimeException("Decode not implemented")))(_.scrape(format))
      .format(new CodecFormat {
        override def mediaType = MediaType.unsafeParse(format)
      })

  private val exampleRegistry = {
    val r = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    Gauge.builder("metric", () => 123).description("description").register(r)
    r
  }

  private val metricsEndpoint = endpoint.get
    .in("metrics")
    .out(
      oneOfBody(
        stringBodyUtf8AnyFormat(metricsCodec(TextFormat.CONTENT_TYPE_OPENMETRICS_100)).example(exampleRegistry),
        stringBodyUtf8AnyFormat(metricsCodec(TextFormat.CONTENT_TYPE_004)).example(exampleRegistry)
      )
    )

  private def metricsLogic(): IO[Either[Unit, PrometheusMeterRegistry]] = IO(Right(registry))

  val endpoints: List[AnyEndpoint] = List(metricsEndpoint)

  val routes: List[ServerEndpoint[Fs2Streams[IO], IO]] = List(metricsEndpoint.serverLogic(_ => metricsLogic()))
}
