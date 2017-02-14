package me.mig.mars.workers.push

import javax.inject.{Inject, Named}

import akka.actor.{ActorRef, ActorSystem}
import akka.kafka.ConsumerMessage.CommittableOffsetBatch
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import me.mig.mars.models.JobModel.PushJob
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}
import play.api.libs.json.Json
import play.api.{Configuration, Logger}

/**
  * Created by jameshsiao on 8/29/16.
  */
class PushNotificationKafkaConsumer @Inject()(configuration: Configuration, system: ActorSystem, implicit val materializer: Materializer, @Named("PushNotificationWorker") pushNotificationWorker: ActorRef) {
  import system.dispatcher

  Logger.info("bootstrap-servers: " + configuration.underlying.getString("kafka.bootstrap-servers"))

  val consumerSettings = ConsumerSettings(system, new ByteArrayDeserializer, new StringDeserializer)
    .withBootstrapServers(configuration.underlying.getString("kafka.bootstrap-servers"))
    .withGroupId("Push")
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  def launch(topic: String) = {
    // Replace all spaces into underscore because Kafka seems not allow space in topic name.
    Logger.info("Consumer launching topic: " + topic.replaceAll(" ", "_"))
    Consumer.committableSource(consumerSettings, Subscriptions.topics(topic.replaceAll(" ", "_")))
      .map { msg =>
        Logger.debug("committableOffset: " + msg.committableOffset)
        Logger.debug("Reading data: " + msg.record.value())
        val pushJob = Json.parse(msg.record.value()).as[PushJob]
        Logger.info("Consumer committable message: " + pushJob)
        // Sending push notification
        pushNotificationWorker ! pushJob
        msg.committableOffset
      }
      .batch(max = 20, first => CommittableOffsetBatch.empty.updated(first)) { (batch, elem) =>
        Logger.debug("Consumer batching update: " + batch + ", elem: " + elem)
        batch.updated(elem)
      }
      .mapAsync(3)(_.commitScaladsl())
      .runWith(Sink.ignore)
      .recover {
        case x => Logger.error("Cosuming data error: " + x)
      }
  }

}
