package me.mig.mars.services

import javax.inject._

import akka.actor.ActorSystem
import akka.stream.Materializer
import me.mig.mars.models.FusionDatabase
import play.api.Logger

/**
  * Created by jameshsiao on 10/17/16.
  */
@Singleton
class PushNotificationService @Inject()(implicit system: ActorSystem, implicit val materializer: Materializer, fusionDB: FusionDatabase) {
//  val config = configuration.underlying.getConfig("kafka")
//  val kafkaProducer: KafkaProducer = new KafkaProducer(config, system, materializer)
//
////  Logger.info("PushNotification test KafkaProducer")
////  kafkaProducer.testProduce()
//  Logger.info("PushNotification test KafkaConsumer")
//  val kafkaConsumer: KafkaConsumer = new KafkaConsumer(config, system, materializer)
//  kafkaConsumer.testConsume()
//  fusionDB.updateIosRegToken(195716838, "�A����ʵ�E��h��8ʙ�6���P����x", "33333333433")

  import scala.concurrent.ExecutionContext.Implicits.global

//  val redis = new RedisService()

  fusionDB.getIosDeviceToken(195716838).map { tokens =>
//    Logger.warn("ios device token: " + ByteString(tokens.head.deviceToken).utf8String)
//    redis.setArrayByte("B:TOKEN", tokens.head.deviceToken)
//    Logger.warn("Get ios token: "+ ByteString(redis.getArrayByte("B:TOKEN").get).utf8String)
    fusionDB.getIosDeviceToken(195713809).map { newtokens =>
      fusionDB.updateIosDeviceToken(195716838, tokens.head.deviceToken, newtokens.head.deviceToken )
    }
  }.recover {
    case ex: Throwable => Logger.error("Get iOS device token error(" + 195716838 + ") error: " + ex.getMessage + ", cause by: " + ex.getCause)
  }
}
