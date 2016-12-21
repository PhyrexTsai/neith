package me.mig.mars.models

import javax.inject.Inject

import akka.actor.ActorSystem
import play.api.Logger
import redis.RedisClient

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Created by jameshsiao on 11/23/16.
  */
class RedisCluster @Inject()(implicit system: ActorSystem) {

  lazy val redis = RedisClient()

  Logger.warn("redis config: " + redis.host + ":" + redis.port)

  def getArrayByte(key: String): Option[Array[Byte]] = {
    Await.result(redis.get[Array[Byte]](key), 100.second)
  }

  def setArrayByte(key: String, value: Array[Byte]): Boolean = {
    Await.result(redis.set(key, value), 100.second)
  }

}
