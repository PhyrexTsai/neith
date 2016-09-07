package workers

import java.io.{File, IOException}
import java.net.URLClassLoader
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import java.util.Calendar

import akka.actor.{Actor, Props}
import com.typesafe.config.Config
import models.NotificationTemplateRepository
import play.api.Logger
import play.twirl.api.Html
import play.twirl.compiler.{GeneratedSource, TwirlCompiler}
import services.TemplateBackgroundService

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.internal.util.Position
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.nsc.{Global, Settings}

/**
  * Created by jameshsiao on 8/30/16.
  */
class TemplateChecker(config: Config, emailTemplateRepo: NotificationTemplateRepository, templateBackgroundService: TemplateBackgroundService) extends Actor {
  import TemplateChecker._
  import models.NotificationMappings._

  class TemplateBuilder(generatedDir: File, generatedClasses: File) {
    implicit val classloader = new URLClassLoader(Array(generatedClasses.toURI.toURL), Class.forName("play.twirl.compiler.TwirlCompiler").getClassLoader)

    // A list of the compile errors from the most recent compiler run
    val compileErrors = new mutable.ListBuffer[CompilationError]

    val compiler = {

      def additionalClassPathEntry: Option[String] = Some(
        Class.forName("play.twirl.compiler.TwirlCompiler").getClassLoader.asInstanceOf[URLClassLoader].getURLs.map(url => new File(url.toURI)).mkString(":"))

      val settings = new Settings
      val scalaObjectSource = Class.forName("scala.Option").getProtectionDomain.getCodeSource

      // is null in Eclipse/OSGI but luckily we don't need it there
      if (scalaObjectSource != null) {
        val compilerPath = Class.forName("scala.tools.nsc.Interpreter").getProtectionDomain.getCodeSource.getLocation
        val libPath = scalaObjectSource.getLocation
        val pathList = List(compilerPath, libPath)
        val origBootclasspath = settings.bootclasspath.value
        settings.bootclasspath.value = ((origBootclasspath :: pathList) ::: additionalClassPathEntry.toList) mkString File.pathSeparator
        settings.outdir.value = generatedClasses.getAbsolutePath
      }

      val compiler = new Global(settings, new ConsoleReporter(settings) {
        override def printMessage(pos: Position, msg: String) = {
          compileErrors.append(CompilationError(msg, pos.line, pos.point))
        }
      })

      compiler
    }

    def compile[T](templateMap: NotificationMappings, additionalImports: Seq[String] = Nil): Unit = {
      val className = templateClassPrefix + templateMap.toString

      // Slick query
      emailTemplateRepo.getTemplateByMapId(templateMap).map { templates =>
        if (templates.length equals 0) {
          Logger.error("No template found: " + templateMap.toString)
          throw new Exception("No template found: " + templateMap.toString)
        } else {
          val template = templates.head
          val templateName = templateMap.toString + templateSuffix
          if (templateBackgroundService.isTemplateUpdated(className, template.timeUpdated.getTime)) {
            Logger.info("Updating template: " + className)
            val templateFile = Files.write(Paths.get(generatedDir.getAbsolutePath, templateName), template.bodyTemplate.getBytes).toFile

            // Because the twirl compiler has to be accessed synchronized, currently we should add synchronized here.
            this.synchronized {

              val Some(generated) = TwirlCompiler.compile(templateFile, generatedDir, generatedDir, "play.twirl.api.HtmlFormat",
                additionalImports = TwirlCompiler.DefaultImports ++ additionalImports)
              val mapper = GeneratedSource(generated)

              val run = new compiler.Run

              compileErrors.clear()

              run.compile(List(generated.getAbsolutePath))

              compileErrors.headOption.foreach {
                case CompilationError(msg, line, column) => {
                  compileErrors.clear()
                  throw CompilationError(msg, mapper.mapLine(line), mapper.mapPosition(column))
                }
              }

            }

            templateBackgroundService.put(className, new CompiledTemplate[T](className), Calendar.getInstance().getTimeInMillis)
            Logger.info("Template " + className + " updated.")
          }
        }
      }.recover {
        case ex: Throwable => Logger.error("Compiling email templates(" + className + ") error: " + ex.getMessage + ", cause by: " + ex.getCause)
      }
    }

  }

  val generatedDir = Paths.get(config.getString("dir") + "/templates")
  val generatedClasses = Paths.get(config.getString("dir") + "/classes")
  val templateBuilder = new TemplateBuilder(generatedDir.toFile, generatedClasses.toFile)
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
    Files.createDirectories(generatedDir)
    Files.createDirectories(generatedClasses)

    Logger.info("Scheduled to check template...")

    templateBuilder.compile[(List[String] => Html)](EMAIL_VERIFICATION)
    templateBuilder.compile[(List[String] => Html)](FORGOT_PASSWORD_EMAIL)
  }

  override def receive: Receive = {
    case ScheduledCheck => checkAndRebuild()
  }
}


object TemplateChecker {
  final val templateClassPrefix = "html."
  final val templateSuffix = ".scala.html"

  case class ScheduledCheck()

  def props(config: Config, emailTemplateRepository: NotificationTemplateRepository, templateBackgroundService: TemplateBackgroundService): Props =
    Props(new TemplateChecker(config, emailTemplateRepository, templateBackgroundService))

  case class CompilationError(message: String, line: Int, column: Int) extends RuntimeException(message)

  class CompiledTemplate[T](className: String)(implicit val classLoader: ClassLoader) {

    private def getF(template: Any) = {
      template.getClass.getMethod("f").invoke(template).asInstanceOf[T]
    }

    def static: T = {
      getF(classLoader.loadClass(className + "$").getDeclaredField("MODULE$").get(null))
    }

    def inject(constructorArgs: Any*): T = {
      classLoader.loadClass(className).getConstructors match {
        case Array(single) => getF(single.newInstance(constructorArgs.asInstanceOf[Seq[AnyRef]]: _*))
        case other => throw new IllegalStateException(className + " does not declare exactly one constructor: " + other)
      }
    }
  }

}