import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import org.specs2.specification.{AfterExample, BeforeExample}
import play.api.libs.Files.TemporaryFile
import play.api.test._

/**
 * add your integration spec here.
 * An integration test will fire up a whole play application in a real (or headless) browser
 */
@RunWith(classOf[JUnitRunner])
class IntegrationSpec extends Specification {

  "Application" should {
    "work from within a browser" in new WithBrowser {

      browser.goTo("http://localhost:" + port)

      browser.pageSource must contain("me.mig.neith.controllers.HealthController.alive")
    }
  }
}
