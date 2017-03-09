package me.mig.neith.controllers

import com.google.inject.Singleton
import play.api.libs.json.Json
import play.api.mvc._

/**
  * Created by phyrextsai on 2017/1/23.
  */
@Singleton
class HealthController extends Controller {

  /**
    * Check service status
    *
    * @return
    */
  def alive = Action {
    Ok(Json.obj("status" -> Json.toJson("OK")))
  }

}
