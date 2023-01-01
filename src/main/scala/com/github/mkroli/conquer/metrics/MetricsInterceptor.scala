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
import io.micrometer.core.instrument.{MeterRegistry, Timer}
import sttp.model.Method
import sttp.monad.MonadError
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.server.model.ServerResponse

import java.time.Duration

case class MetricsInterceptor(registry: MeterRegistry) extends EndpointInterceptor[IO] {
  override def apply[B](responder: Responder[IO, B], endpointHandler: EndpointHandler[IO, B]): EndpointHandler[IO, B] = new EndpointHandler[IO, B] {
    override def onDecodeSuccess[A, U, I](
        ctx: DecodeSuccessContext[IO, A, U, I]
    )(implicit monad: MonadError[IO], bodyListener: BodyListener[IO, B]): IO[ServerResponse[B]] = {
      endpointHandler.onDecodeSuccess(ctx).timed.flatMap {
        case (timed, response) => {
          val timer = IO {
            Timer
              .builder("http.requests")
              .tag("method", ctx.endpoint.method.getOrElse(Method.GET).method)
              .tag("endpoint", ctx.endpoint.showPathTemplate())
              .maximumExpectedValue(Duration.ofMillis(10000))
              .publishPercentileHistogram()
              .register(registry)
              .record(Duration.ofMillis(timed.toMillis))
          }
          timer >> IO.pure(response)
        }
      }
    }

    override def onSecurityFailure[A](
        ctx: SecurityFailureContext[IO, A]
    )(implicit monad: MonadError[IO], bodyListener: BodyListener[IO, B]): IO[ServerResponse[B]] = {
      endpointHandler.onSecurityFailure(ctx)
    }

    override def onDecodeFailure(
        ctx: DecodeFailureContext
    )(implicit monad: MonadError[IO], bodyListener: BodyListener[IO, B]): IO[Option[ServerResponse[B]]] = {
      endpointHandler.onDecodeFailure(ctx)
    }
  }
}
