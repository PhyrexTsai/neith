package me.mig.mars.controllers

import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import me.mig.mars.ErrorHandler
import me.mig.mars.models.JobModel.{CreateUpdateJob, CreateUpdateJobAck, DeleteJobAck, GetJobsAck}
import me.mig.mars.models.NotificationModel.GetNotificationTypesAck
import me.mig.mars.services.JobScheduleService
import me.mig.mars.workers.push.PushNotificationWorker
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
  def createUpdateJob = Action.async(ErrorHandler.validateJson[CreateUpdateJob]) { request =>
    Source.single(request.body)
      .via(jobScheduleService.createUpdateJob)
      .runWith(Sink.head)
      .map( result => Ok(Json.toJson(result)) )
      .recover {
        case x: Throwable => BadRequest(
          Json.toJson( CreateUpdateJobAck(false, Some("Updating a scheduled job encounters error: " + x.getMessage)) )
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
        case x: Throwable => BadRequest(
          Json.toJson( GetJobsAck(List.empty, Some("Getting job list encounters error: " + x.getMessage)) )
        )
      }
  }

  def deleteJob(id: String) = Action.async { request =>
    Source.single(id)
      .via(jobScheduleService.deleteJob)
      .runWith(Sink.head)
      .map( result => Ok(Json.toJson(result)) )
      .recover {
        case x: Throwable => BadRequest(
          Json.toJson( DeleteJobAck(false, Some("Deleting job [" + id + "] encounters error: " + x.getMessage)) )
        )
      }
  }

  def getNotificationTypes = Action.async { request =>
    Source.single(1)
      .via(jobScheduleService.getNotificationTypes())
      .runWith(Sink.head)
      .map( result => Ok(Json.toJson(result)) )
      .recover {
        case x: Throwable => BadRequest(
          Json.toJson( GetNotificationTypesAck(List.empty, Some("Getting notification types encounters error: " + x.getMessage)) )
        )
      }
  }

  def fakeNotification(`type`: String) = Action.async { request =>
    Source.single(1)
      .map { nothing =>
        PushNotificationWorker.toGcmMessage(PushNotificationWorker.generateCallToAction(Map("type" -> `type`, "value" -> "1234567890")), s"push ${`type`} notification", 1234567, "fake user")
      }
      .runWith(Sink.head)
      .map(result => Ok(Json.parse(result)))
  }

}
