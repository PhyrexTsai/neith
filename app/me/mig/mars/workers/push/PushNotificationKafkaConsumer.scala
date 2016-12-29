package me.mig.mars.workers.push

import javax.inject.Inject

import akka.actor.ActorSystem
import akka.kafka.ConsumerMessage.CommittableOffsetBatch
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}
import play.api.{Configuration, Logger}

/**
  * Created by jameshsiao on 8/29/16.
  */
class PushNotificationKafkaConsumer @Inject()(configuration: Configuration, system: ActorSystem, implicit val materializer: Materializer) {
  val consumerSettings = ConsumerSettings(system, new ByteArrayDeserializer, new StringDeserializer)
    .withBootstrapServers(configuration.getString("kafka.host").get + ":" + configuration.getInt("kafka.port").get)
    .withGroupId("Push")
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  def launch(topic: String) = {
    Consumer.committableSource(consumerSettings, Subscriptions.topics(topic))
      .map { msg =>
        Logger.info("Consumer committable message: " + msg.record.value())
        // TODO: Send push notification
        msg.committableOffset
      }
      .batch(max = 20, first => CommittableOffsetBatch.empty.updated(first)) { (batch, elem) =>
        Logger.debug("Consumer batching update: " + batch + ", elem: " + elem)
        batch.updated(elem)
      }
      .mapAsync(3)(_.commitScaladsl())
      .runWith(Sink.ignore)
  }

}
