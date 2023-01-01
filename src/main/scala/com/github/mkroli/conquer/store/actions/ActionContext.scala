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

package com.github.mkroli.conquer.store.actions

import cats.effect.{IO, Resource}
import com.github.mkroli.conquer.Settings
import fs2.kafka.Serializer
import fs2.kafka.vulcan.KafkaAvroSerializer
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import org.apache.avro.generic.{GenericDatumWriter, GenericRecord}
import org.apache.avro.io.EncoderFactory

import java.io.ByteArrayOutputStream
import scala.jdk.CollectionConverters._

case class ActionContext(
    settings: Settings,
    schemaRegistryClient: SchemaRegistryClient
) {
  lazy val avroSerializer = {
    val kas = new KafkaAvroSerializer(
      schemaRegistryClient,
      Map(
        AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG -> settings.schemaRegistry.url,
        AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS      -> "true"
      ).asJava
    )
    Serializer.instance[IO, GenericRecord]((topic, headers, data) => IO(kas.serialize(topic, headers.asJava, data)))
  }

  lazy val jsonSerializer = {
    Serializer.instance[IO, GenericRecord] { (_, _, data) =>
      Resource.fromAutoCloseable(IO(new ByteArrayOutputStream())).use { bos =>
        IO {
          val enc    = EncoderFactory.get().jsonEncoder(data.getSchema, bos)
          val writer = new GenericDatumWriter[GenericRecord](data.getSchema)
          writer.write(data, enc)
          enc.flush()
          bos.toByteArray
        }
      }
    }
  }
}
