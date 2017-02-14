package me.mig.mars.workers.push

import javax.inject.Inject

import akka.actor.{Actor, ActorSystem}
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import me.mig.mars.models.JobModel.PushJob
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}
import play.api.libs.json.Json
import play.api.{Configuration, Logger}

/**
  * Created by jameshsiao on 8/29/16.
  */
class PushNotificationKafkaProducer @Inject()(configuration: Configuration, system: ActorSystem, implicit val materializer: Materializer) extends Actor {
  import system.dispatcher

  private val producerSettings = ProducerSettings(system, new ByteArraySerializer, new StringSerializer)
    .withBootstrapServers(configuration.underlying.getString("kafka.bootstrap-servers"))

  override def receive: Receive = {
    case pushJob: PushJob =>
      Source.single(pushJob)
        // Replace all spaces into underscore because Kafka seems not allow space in topic name.
        .map( job => new ProducerRecord[Array[Byte], String](job.jobId.replaceAll(" ", "_"), Json.toJson[PushJob](job).toString) )
        .runWith(Producer.plainSink(producerSettings))
        .recover {
          case x: Throwable => Logger.error("Produce kafka error: " + x.getMessage)
        }
    case x => Logger.warn("Unsupported event: " + x)
  }
}
