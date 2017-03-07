package me.mig.mars

import io.gatling.http.Predef._
import io.gatling.core.Predef._

import scala.concurrent.duration._

/**
  * Created by jameshsiao on 9/29/16.
  */
class EmailSimulation extends Simulation {
  val testConcurrent = 10

  val httpConf = http
    .baseURL("http://orion-mars01.stg.sjc02.projectgoth.com:9000") // Should route via HA-proxy because of getting user ID request
    .acceptHeader("text/html,application/json,application/xml;q=0.9,*/*;q=0.8") // Here are the common headers
    .doNotTrackHeader("1")

  val emailSim = scenario("Email notification")
    .exec(http("Email Verification")
      .post("/sendVerificationEmail")
      .body(
        StringBody(
          """
            {
              "username": "James",
              "email": "james.h@mig.me",
              "verifyLink": "http://tw.yahoo.com"
            }
          """
        )
      ).asJSON
      .check(jsonPath("$.isSuccess").is("true"))
    )

  /**
    * <link>http://gatling.io/docs/2.2.2/general/simulation_setup.html</link>
    * 1.  nothingFor(duration): Pause for a given duration.
    * 2.  atOnceUsers(nbUsers): Injects a given number of users at once.
    * 3.  rampUsers(nbUsers) over(duration): Injects a given number of users with a linear ramp over a given duration.
    * 4.  constantUsersPerSec(rate) during(duration): Injects users at a constant rate, defined in users per second, during a given duration. Users will be injected at regular intervals.
    * 5.  constantUsersPerSec(rate) during(duration) randomized: Injects users at a constant rate, defined in users per second, during a given duration. Users will be injected at randomized intervals.
    * 6.  rampUsersPerSec(rate1) to (rate2) during(duration): Injects users from starting rate to target rate, defined in users per second, during a given duration. Users will be injected at regular intervals.
    * 7.  rampUsersPerSec(rate1) to(rate2) during(duration) randomized: Injects users from starting rate to target rate, defined in users per second, during a given duration. Users will be injected at randomized intervals.
    * 8.  splitUsers(nbUsers) into(injectionStep) separatedBy(duration): Repeatedly execute the defined injection step separated by a pause of the given duration until reaching nbUsers, the total number of users to inject.
    * 9.  splitUsers(nbUsers) into(injectionStep1) separatedBy(injectionStep2): Repeatedly execute the first defined injection step (injectionStep1) separated by the execution of the second injection step (injectionStep2) until reaching nbUsers, the total number of users to inject.
    * 10. heavisideUsers(nbUsers) over(duration): Injects a given number of users following a smooth
    */
  setUp(
    emailSim.inject(
      nothingFor(4 seconds), // 1
      atOnceUsers(testConcurrent) // 2
      //      rampUsers(100) over(5 seconds), // 3
      //      constantUsersPerSec(20) during(15 seconds), // 4
      //      constantUsersPerSec(20) during(15 seconds) randomized, // 5
      //      rampUsersPerSec(10) to 20 during(10 minutes), // 6
      //      rampUsersPerSec(10) to 20 during(10 minutes) randomized, // 7
      //      splitUsers(1000) into(rampUsers(10) over(10 seconds)) separatedBy(10 seconds), // 8
      //      splitUsers(1000) into(rampUsers(10) over(10 seconds)) separatedBy atOnceUsers(30), // 9
      //      heavisideUsers(1000) over(20 seconds) // 10
    ).protocols(httpConf))
}
