package me.mig.neith.controllers

import play.api.libs.json.Json
import play.api.mvc._

/**
  * Created by phyrextsai on 2017/1/23.
  */
class HealthController extends Controller {

  def alive = Action {
    Ok(Json.obj("status" -> Json.toJson("OK")))
  }

}
