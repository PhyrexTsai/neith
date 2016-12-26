package me.mig.mars.models

/**
  * Created by jameshsiao on 12/15/16.
  */
object NotificationType extends Enumeration {
  type NotificationType = Value

  val EMAIL = Value(1, "EMail")
  val SMS = Value(2, "SMS")
  val PUSH = Value(3, "Push")
  val ALERT = Value(4, "Alert")
}
