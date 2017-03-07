package me.mig.mars.models

/**
  * Created by jameshsiao on 12/23/16.
  */
object UserModel {
  case class UserId(id: Int, username: String)
  case class User(username: String, countryId: Int)
  case class UserLabel(userId: Int, `type`: Short)
}
