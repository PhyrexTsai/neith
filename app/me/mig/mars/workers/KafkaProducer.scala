package me.mig.mars.workers

import akka.actor.ActorSystem
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.typesafe.config.Config
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}
import play.api.Logger

/**
  * Created by jameshsiao on 8/29/16.
  */
class KafkaProducer(config: Config, system: ActorSystem, implicit val materializer: Materializer) {
  val producerSettings = ProducerSettings(system, new ByteArraySerializer, new StringSerializer)
    .withBootstrapServers(config.getString("host") + ":" + config.getInt("port"))

  def testProduce() = {
    val done = Source(1 to 100)
      .map(_.toString)
      .map { elem =>
        Logger.info("Kafka host: " + config.getString("host") + ":" + config.getString("port"))
        Logger.info("Create producer record: " + elem)
        new ProducerRecord[Array[Byte], String]("TestTopic", elem)
      }
      .runWith(Producer.plainSink(producerSettings))
  }
}
