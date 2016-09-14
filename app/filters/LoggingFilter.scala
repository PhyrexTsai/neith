package filters

import akka.stream.Materializer
import javax.inject._

import play.api.Logger
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

/**
 * This is a simple filter that adds a header to all requests. It's
 * added to the application's list of filters by the
 * [[Filters]] class.
 *
 * @param mat This object is needed to handle streaming of requests
 * and responses.
 * @param exec This class is needed to execute code asynchronously.
 * It is used below by the `map` method.
 */
@Singleton
class LoggingFilter @Inject()(
    implicit override val mat: Materializer,
    exec: ExecutionContext) extends Filter {

  override def apply(nextFilter: RequestHeader => Future[Result])
           (requestHeader: RequestHeader): Future[Result] = {
    val startTime = System.currentTimeMillis

    nextFilter(requestHeader).map { result =>

      val requestTime = System.currentTimeMillis - startTime

      // TODO this should be determined whether request body is necessary for tracing.
      Logger.info(s"MARS REST: ${result.header.status} ${requestHeader.method} ${requestHeader.uri} $requestTime $logHeaders ")

      def logHeaders =
        requestHeader.headers.toSimpleMap.toSeq.sortBy(_._1).collect {
          case ("User-Agent", v) => s"User-Agent: $v"
          case ("user-agent", v) => s"User-Agent: $v"
          case ("sessionId", v) => s"sessionId: $v"
          case ("X-Forwarded-For", v) => s"X-Forwarded-For: $v"
        }

      result
    }
  }

}
