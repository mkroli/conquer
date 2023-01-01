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

import cats.implicits._
import com.github.mkroli.conquer.store._
import com.github.mkroli.conquer.store.actions.{Action, Transformation, TransformedAction}
import com.github.mkroli.conquer.store.index.{Attribute, BooleanAttribute, DecimalAttribute, StringAttribute}
import io.circe._
import io.circe.syntax._

trait ApiCodecs {
  implicit val attributeEncoder: Encoder[Attribute] = Encoder.instance {
    case StringAttribute(s)  => Json.fromString(s)
    case DecimalAttribute(d) => Json.fromBigDecimal(d)
    case BooleanAttribute(b) => Json.fromBoolean(b)
  }

  implicit val attributeDecoder: Decoder[Attribute] = Decoder.decodeJson.emapTry { json =>
    val string  = json.as[String].map(StringAttribute.apply)
    val decimal = json.as[BigDecimal].map(DecimalAttribute.apply)
    val boolean = json.as[Boolean].map(BooleanAttribute.apply)
    (string orElse decimal orElse boolean).toTry
  }

  implicit val attributeRulesEncoder: Encoder[Set[AttributeRule]] = {
    Encoder.encodeJson.contramap(r => Json.obj(r.map(r => (r.key, r.id.asJson)).toList: _*))
  }

  implicit val attributeRulesDecoder: Decoder[Set[AttributeRule]] = {
    Decoder.decodeJsonObject.emapTry { obj =>
      obj.toList
        .map {
          case (key, id) => id.as[Attribute].map(id => AttributeRule(key, id))
        }
        .sequence
        .map(_.toSet)
        .toTry
    }
  }

  implicit def ruleEncoder(
      implicit actionEnc: Encoder[Action],
      transformationsEnc: Encoder[Transformation]
  ): Encoder[Rule] = {
    Encoder.encodeJson.contramap {
      case Rule(attributes, TransformedAction(action, transformations)) =>
        Json.obj(
          "attributes"      -> implicitly[Encoder[Set[AttributeRule]]].apply(attributes),
          "action"          -> implicitly[Encoder[Action]].apply(action),
          "transformations" -> implicitly[Encoder[Seq[Transformation]]].apply(transformations)
        )
      case Rule(attributes, action) =>
        Json.obj(
          "attributes" -> implicitly[Encoder[Set[AttributeRule]]].apply(attributes),
          "action"     -> implicitly[Encoder[Action]].apply(action)
        )
    }
  }

  implicit def ruleDecoder(
      implicit actionDec: Decoder[Action],
      transformationsDec: Decoder[Transformation]
  ): Decoder[Rule] = {
    Decoder.decodeJsonObject.emapTry { obj =>
      val attrs = obj("attributes")
        .map(_.as[Set[AttributeRule]])
        .getOrElse(Left(DecodingFailure("missing attribute", Nil)))
      val action = obj("action")
        .map(_.as[Action])
        .getOrElse(Left(DecodingFailure("missing attribute", Nil)))
      val transformations = obj("transformations")
        .map(_.as[Seq[Transformation]])
        .sequence
      val rule = for {
        attrs           <- attrs
        action          <- action
        transformations <- transformations
      } yield {
        transformations match {
          case Some(transformations) => Rule(attrs, TransformedAction(action, transformations))
          case None                  => Rule(attrs, action)
        }
      }
      rule.toTry
    }
  }
}
