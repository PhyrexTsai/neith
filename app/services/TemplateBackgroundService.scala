package services

import java.io.File
import java.net.URLClassLoader
import java.util.Calendar
import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Logger}
import play.twirl.compiler.{GeneratedSource, TwirlCompiler}
import workers.TemplateChecker
import workers.TemplateChecker.ScheduledCheck

import scala.collection.mutable
import scala.concurrent.Future
import scala.reflect.internal.util.Position
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.nsc.{Global, Settings}

/**
  * Created by jameshsiao on 8/29/16.
  */

@Singleton
class TemplateBackgroundService @Inject()(system: ActorSystem, configuration: Configuration, appLifecycle: ApplicationLifecycle) {
  import system.dispatcher

  import scala.concurrent.duration._

  val templateConfig = configuration.underlying.getConfig("mars.templates")

  // Dynamically build templates @runtime
  // Start scheduler to check email templates if need to rebuild every 5 mins.
  val cancelable = system.scheduler.schedule(0 seconds, 300 seconds, system.actorOf(TemplateChecker.props(templateConfig)), ScheduledCheck)

  appLifecycle.addStopHook { () =>
    //    val stop: Instant = clock.instant
    //    val runningTime: Long = stop.getEpochSecond - start.getEpochSecond
    //    Logger.info(s"ApplicationTimer demo: Stopping application at ${clock.instant} after ${runningTime}s.")
    Future.successful(cancelable.cancel())
  }
}

object TemplateBackgroundService {
  val templates = mutable.HashMap[String, (Any, Long)]()
  case class CompilationError(message: String, line: Int, column: Int) extends RuntimeException(message)

  class TemplateBuilder(sourceDir: File, generatedDir: File, generatedClasses: File) {
    val twirlCompiler = TwirlCompiler

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

    def compile[T](templateName: String, className: String, additionalImports: Seq[String] = Nil): Unit = {
      val templateFile = new File(sourceDir, templateName)
      if (isTemplateUpdated(className, templateFile.lastModified())) {
        Logger.info("Updating template: " + className)
        val Some(generated) = twirlCompiler.compile(templateFile, sourceDir, generatedDir, "play.twirl.api.HtmlFormat",
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

        TemplateBackgroundService.put(className, new CompiledTemplate[T](className), Calendar.getInstance().getTimeInMillis)
        Logger.info("Template " + className + " updated.")
      }
      //    RuntimeTemplateBuilder.get(className).asInstanceOf[CompiledTemplate[T]]
    }
  }

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

  def get[T](className: String): CompiledTemplate[T] = {
    val compiledTemplate = templates.get(className)
    if (compiledTemplate isEmpty) null
    else compiledTemplate.get._1.asInstanceOf[CompiledTemplate[T]]
  }
  def put(className: String, template: Any, lastUpdated: Long): Unit = {
    templates.put(className, (template, lastUpdated))
  }

  def isTemplateUpdated(className: String, updatedTime: Long): Boolean = {
    templates.get(className) match {
      case Some((template, lastUpdated)) =>
        updatedTime > lastUpdated
      case None => true
    }
  }
}