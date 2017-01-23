package me.mig.neith.filters

//import java.util.zip.GZIPInputStream
import javax.inject.Inject

import akka.stream.Materializer
import akka.util.ByteString
//import org.apache.commons.io.IOUtils
import play.api.Logger
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by phyrextsai on 2017/1/23.
  */
class LoggingFilter @Inject()(implicit val mat: Materializer, ec: ExecutionContext) extends Filter {

  def apply(nextFilter: RequestHeader => Future[Result])
           (requestHeader: RequestHeader): Future[Result] = {

    val startTime = System.currentTimeMillis

    nextFilter(requestHeader).map { result =>

      val requestTime = System.currentTimeMillis - startTime
      val headers = requestHeader.headers

      // TODO should be determined whether request body is necessary for tracing.
      Logger.info(s"NEITH REST: ${result.header.status} ${requestHeader.method} ${requestHeader.uri.split("\\?")(0)} $requestTime $logHeaders")
      if(result.header.status != 200)   // log detail of error request
        result.body.consumeData.map(logErrorRequest)

      def logHeaders =
        headers.toMap
          .map(x => (x._1.toLowerCase, x._2.headOption.getOrElse("")))(collection.breakOut)
          .sortBy(_._1).collect {
          case ("user-agent", v) => s"User-Agent: $v"
          case ("sessionid", v) => s"sessionId: $v"
          case ("x-forwarded-for", v) => s"X-Forwarded-For: $v"
        }

      def logErrorRequest(respBody: ByteString) = {
        //val gzipStream = new GZIPInputStream(respBody.iterator.asInputStream)
        Logger.warn(s"Request error: ${result.header.status} $headers\r\n${respBody.iterator.asInputStream}")
      }
      result
    }
  }

}
