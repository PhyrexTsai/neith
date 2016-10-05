package me.mig.mars.controllers

import javax.inject._

import play.api.mvc._

/**
  * This controller creates an `Action` to handle HTTP requests to the
  * application's home page.
  */
@Singleton
class HomeController @Inject() extends Controller {

  /**
    * Always redirect to the service health checking page since we don't need the home page now.
    */
  def index = Action {
    Redirect("/health")
  }

}