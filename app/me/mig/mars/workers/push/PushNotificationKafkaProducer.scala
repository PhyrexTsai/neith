package me.mig.mars.workers.push

import javax.inject.Inject

import akka.actor.{Actor, ActorSystem}
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import me.mig.mars.models.JobModel.JobToken
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}
import play.api.{Configuration, Logger}

/**
  * Created by jameshsiao on 8/29/16.
  */
class PushNotificationKafkaProducer @Inject()(configuration: Configuration, system: ActorSystem, implicit val materializer: Materializer) extends Actor {

  private val producerSettings = ProducerSettings(system, new ByteArraySerializer, new StringSerializer)
    .withBootstrapServers(configuration.getString("kafka.host").get + ":" + configuration.getInt("kafka.port").get)

  override def receive: Receive = {
    case token: JobToken =>
      Source.single(token)
        .map(job => {
          new ProducerRecord[Array[Byte], String](job.jobId.get, job.toString)
        })
        .runWith(Producer.plainSink(producerSettings))
    case x => Logger.warn("Unsupported event: " + x)
  }
}
