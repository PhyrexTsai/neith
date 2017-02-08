package me.mig.mars.models

/**
  * Created by jameshsiao on 12/15/16.
  */
object NotificationType extends Enumeration {
  type NotificationType = Value

  val EMAIL = Value("EMail")
  val SMS = Value("SMS")
  val PUSH = Value("Push")
  val POPUP = Value("Pop-up")
}
