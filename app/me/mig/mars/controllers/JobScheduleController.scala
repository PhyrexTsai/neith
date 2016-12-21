package me.mig.mars.controllers

import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import me.mig.mars.ErrorHandler
import me.mig.mars.services.JobScheduleService
import me.mig.mars.services.JobScheduleService._
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}

/**
  * Created by jameshsiao on 12/13/16.
  */
@Singleton
class JobScheduleController @Inject()(jobScheduleService: JobScheduleService, system: ActorSystem, implicit val materializer: Materializer) extends Controller {
  import system.dispatcher

  def createJob = Action.async(ErrorHandler.validateJson[CreateJob]) { request =>
      Source.single(request.body)
        .via(jobScheduleService.createJob)
        .recover {
          case x => CreateJobAck(false, Some("Creating a scheduled job encounters error: " + x.getMessage ))
        }
        .runWith(Sink.head)
        .map(result => result.success match {
          case true => Ok(Json.toJson(result))
          case false => BadRequest(Json.toJson(result))
        })
  }

  def getJobs(id: Int, page: Option[Int], pageSize: Option[Int]) = Action.async { request =>
    Logger.info("id: " + id)
    Source.single(id)
      .via(jobScheduleService.getJobs)
      .recover {
        case x => GetJobsAck(List.empty, Some("Getting job list encounters error: " + x.getMessage))
      }
      .runWith(Sink.head)
      .map(result => result.error match {
        case None => Ok(Json.toJson(result))
        case _ => BadRequest(Json.toJson(result))
      })
  }

}
