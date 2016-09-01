package workers

import java.io.{File, IOException}
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file._

import akka.actor.{Actor, Props}
import com.typesafe.config.Config
import play.api.Logger
import play.twirl.api.Html
import services.TemplateBackgroundService.TemplateBuilder

/**
  * Created by jameshsiao on 8/30/16.
  */
class TemplateChecker(config: Config) extends Actor {
  import TemplateChecker._

  val templateBuilder = new TemplateBuilder(new File(config.getString("dir")), generatedDir.toFile, generatedClasses.toFile)
  val recursiveFileDeleter = new FileVisitor[Path] {
    def visitFileFailed(file: Path, exc: IOException) = FileVisitResult.CONTINUE

    def visitFile(file: Path, attrs: BasicFileAttributes) = {
      Files.delete(file)
      FileVisitResult.CONTINUE
    }

    def preVisitDirectory(dir: Path, attrs: BasicFileAttributes) = FileVisitResult.CONTINUE

    def postVisitDirectory(dir: Path, exc: IOException) = {
      Files.delete(dir)
      FileVisitResult.CONTINUE
    }
  }

  def checkAndRebuild(): Unit = {
    Files.walkFileTree(generatedDir, recursiveFileDeleter)
    Files.walkFileTree(generatedClasses, recursiveFileDeleter)
    Files.createDirectories(generatedClasses)

    Logger.info("Scheduled to check template...")

    templateBuilder.compile[((String, String) => Html)]("emailVerify.scala.html", "html.emailVerify")
  }

  override def receive: Receive = {
    case ScheduledCheck => checkAndRebuild()
  }
}


object TemplateChecker {
  val generatedDir = Paths.get("/usr/local/mars/generated/templates")
  val generatedClasses = Paths.get("/usr/local/mars/generated/classes")

  case class ScheduledCheck()

  def props(config: Config): Props = Props(new TemplateChecker(config))
}