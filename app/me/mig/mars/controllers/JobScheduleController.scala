package me.mig.mars.controllers

import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import me.mig.mars.ErrorHandler
import me.mig.mars.models.JobModel.{CreateJob, CreateJobAck, GetJobsAck}
import me.mig.mars.services.JobScheduleService
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}

/**
  * Created by jameshsiao on 12/13/16.
  */
@Singleton
class JobScheduleController @Inject()(jobScheduleService: JobScheduleService, system: ActorSystem, implicit val materializer: Materializer) extends Controller {
  import system.dispatcher

  /**
    * Read request from json body in CreateJson, and return the success in Boolean.
    *
    * @return       Success in TRUE or FALSE with error message if the job hasn't created.
    */
  def createJob = Action.async(ErrorHandler.validateJson[CreateJob]) { request =>
      Source.single(request.body)
        .via(jobScheduleService.createJob)
        .runWith(Sink.head)
        .map( result => Ok(Json.toJson(result)) )
        .recover {
          case x => BadRequest(
            Json.toJson( CreateJobAck(false, Some("Creating a scheduled job encounters error: " + x.getMessage)) )
          )
        }
  }

  /**
    * Read GET request with query string,
    *
    * @param id       Optional, job id in String, if not set, will get all stored jobs.
    * @param page     Optional, page number of jobs to fetch according the pageSize.
    * @param pageSize Optional, return jobs amount per page, default is ??.
    * @return         List of jobs in data field or error message in error field (Optional) if encounters errors.
    */
  def getJobs(id: String, page: Option[Int], pageSize: Option[Int]) = Action.async { request =>
    Source.single(id)
      .via(jobScheduleService.getJobs)
      .runWith(Sink.head)
      .map( result => Ok(Json.toJson(result)) )
      .recover {
        case x => BadRequest(
          Json.toJson( GetJobsAck(List.empty, Some("Getting job list encounters error: " + x.getMessage)) )
        )
      }
  }

}
