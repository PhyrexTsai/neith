package me.mig.mars.models

/**
  * Created by jameshsiao on 12/15/16.
  */
object NotificationType extends Enumeration {
  type NotificationType = Value

  val EMAIL = Value(1)
  val SMS = Value(2)
  val PUSH = Value(3)
  val ALERT = Value(4)
}
