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

  Logger.info("bootstrap-servers: " + configuration.underlying.getString("akka.kafka.bootstrap.servers"))

  private final val GROUP_ID = "Push"

  private var consumedCount = 0

  private val consumerSettings = ConsumerSettings(system, new ByteArrayDeserializer, new StringDeserializer)
    .withBootstrapServers(configuration.underlying.getString("akka.kafka.bootstrap.servers"))
    .withGroupId(GROUP_ID)
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
//  private val consumerPool: ActorRef = system.actorOf(new SmallestMailboxPool(5).props(KafkaConsumerActor.props(consumerSettings)))

  def assign(topic: String) = {
    // Replace all spaces into underscore because Kafka seems not allow space in topic name.
    val validTopic = topic.replaceAll(" ", "_")
    Logger.info("Consumer launching topic: " + validTopic)
//    Consumer.committableExternalSource[Array[Byte], String](
//      consumerPool,
//      Subscriptions.assignment(
//        new TopicPartition(validTopic, 1)
//      ),
//      GROUP_ID,
//      10 seconds)
    Consumer.committableSource(consumerSettings, Subscriptions.topics(validTopic))
      .map { msg =>
        Logger.debug("committableOffset: " + msg.committableOffset)
        Logger.debug("Reading data: " + msg.record.value())
        val pushJob = Json.parse(msg.record.value()).as[PushJob]
        Logger.info("Consumer committable message: " + pushJob)
        // Sending push notification
        pushNotificationWorker ! pushJob
        consumedCount += 1
        Logger.debug("Consumed count: " + consumedCount)
        msg.committableOffset
      }
      .batch(max = 20, first => CommittableOffsetBatch.empty.updated(first)) { (batch, elem) =>
        Logger.debug("Consumer batching update: " + batch + ", elem: " + elem)
        batch.updated(elem)
      }
      .mapAsync(3)(_.commitScaladsl())
      .runWith(Sink.ignore)
      .recover {
        case x: Throwable => Logger.error("Kafka Consuming data error: " + x.getMessage)
      }
  }

}
