package com.codacy.configuration.parser

import java.io.File
import caseapp._
import caseapp.core.Error
import caseapp.core.app
import caseapp.core.argparser.ArgParser
import com.codacy.api.OrganizationProvider
import com.codacy.configuration.parser.ConfigArgumentParsers._
import com.codacy.parsers.CoverageParser
import com.codacy.parsers.implementation._
// Intellij keeps removing this import, I'll leave it here for future reference
// import com.codacy.configuration.parser.ConfigArgumentParsers._

abstract class ConfigurationParsingApp extends app.CommandsEntryPoint { self =>

  object ReportCommand extends app.Command[Report] {

    def run(options: Report, remainingArgs: RemainingArgs): Unit =
      sys.exit(self.run(options))
  }

  object FinalCommand extends app.Command[Final] {

    def run(options: Final, remainingArgs: RemainingArgs): Unit =
      sys.exit(self.run(options))
  }

  def commands = Seq(ReportCommand, FinalCommand)
  def progName: String = "codacy-coverage-reporter"

  def run(config: CommandConfiguration): Int
}

@AppName("codacy-coverage-reporter")
@AppVersion(Option(BaseCommand.getClass.getPackage.getImplementationVersion).getOrElse("dev"))
@ProgName(BaseCommand.runToolCommand)
case class BaseCommand()

object BaseCommand {

  private def runningOnNativeImage: Boolean = {
    val graalVMFlag = Option(System.getProperty("org.graalvm.nativeimage.kind"))
    graalVMFlag.map(p => p == "executable" || p == "shared").getOrElse(false)
  }

  def runToolCommand = {
    if (runningOnNativeImage) {
      "codacy-coverage-reporter"
    } else {
      s"java -jar codacy-coverage-reporter-assembly-${Option(BaseCommand.getClass.getPackage.getImplementationVersion)
        .getOrElse("dev")}.jar"
    }
  }
}

sealed trait CommandConfiguration {
  def baseConfig: BaseCommandConfig
}

case class Final(
    @Recurse
    baseConfig: BaseCommandConfig
) extends CommandConfiguration

case class Report(
    @Recurse
    baseConfig: BaseCommandConfig,
    @Name("l") @ValueDescription("language associated with your coverage report")
    language: Option[String] = None,
    @Hidden @Name("f")
    forceLanguage: Int @@ Counter = Tag.of(0),
    @Name("r") @ValueDescription("your project coverage file name (supports globs)")
    coverageReports: Option[List[File]] = None,
    @ValueDescription("if the report is partial")
    partial: Int @@ Counter = Tag.of(0),
    @ValueDescription("the project path prefix")
    prefix: Option[String] = None,
    @ValueDescription("your coverage parser")
    @HelpMessage(s"Available parsers are: ${ConfigArgumentParsers.parsersMap.keys.mkString(",")}")
    forceCoverageParser: Option[CoverageParser] = None
) extends CommandConfiguration {
  val partialValue: Boolean = partial.## > 0
  val forceLanguageValue: Boolean = forceLanguage.## > 0
}

case class BaseCommandConfig(
    @Name("t") @ValueDescription("your project API token")
    projectToken: Option[String] = None,
    @Name("a") @ValueDescription("your account api token")
    apiToken: Option[String] = None,
    @ValueDescription("organization provider")
    organizationProvider: Option[OrganizationProvider.Value] = None,
    @Name("u") @ValueDescription("your username")
    username: Option[String] = None,
    @Name("p") @ValueDescription("project name")
    projectName: Option[String] = None,
    @ValueDescription("the base URL for the Codacy API")
    codacyApiBaseUrl: Option[String] = None,
    @ValueDescription("your commit SHA-1 hash")
    commitUUID: Option[String] = None,
    @ValueDescription(
      "Sets a specified read timeout value, in milliseconds, to be used when interacting with Codacy API. By default, the value is 10 seconds"
    )
    httpTimeout: Int = 10000,
    @ValueDescription(
      "Sets a specified time, in milliseconds, to be used when waiting between retries. By default, the value is 10 seconds"
    )
    sleepTime: Int = 10000,
    @ValueDescription("Sets a number of retries in case of failure. By default, the value is 3 times")
    numRetries: Int = 3,
    @Name("s") @ValueDescription("skip if token isn't defined")
    skip: Int @@ Counter = Tag.of(0),
    @Hidden
    debug: Int @@ Counter = Tag.of(0),
    @ExtraName("i") @ValueDescription(
      "[default: false] - Skip the SSL certificate verification when communicating with the Codacy API"
    )
    skipSslVerification: Int @@ Counter = Tag.of(0)
) {
  val skipValue: Boolean = skip.## > 0
  val debugValue: Boolean = debug.## > 0
  val skipSslVerificationValue: Boolean = skipSslVerification.## > 0
}

object ConfigArgumentParsers {

  implicit val fileParser: ArgParser[File] = new ArgParser[File] {
    def apply(current: Option[File], index: Int, span: Int, value: String) = Right(new File(value))
    def description = "file"
  }

  val parsersMap = Map(
    "cobertura" -> CoberturaParser,
    "jacoco" -> JacocoParser,
    "clover" -> CloverParser,
    "opencover" -> OpenCoverParser,
    "dotcover" -> DotcoverParser,
    "phpunit" -> PhpUnitXmlParser,
    "lcov" -> LCOVParser,
    "go" -> GoParser
  )

  implicit val coverageParser: ArgParser[CoverageParser] = new ArgParser[CoverageParser] {

    def apply(current: Option[CoverageParser], index: Int, span: Int, v: String) = {
      val value = v.trim.toLowerCase
      parsersMap.get(value) match {
        case Some(parser) => Right(parser)
        case _ =>
          Left(
            Error.Other(
              s"${value} is an unsupported/unrecognized coverage parser. (Available patterns are: ${parsersMap.keys.mkString(",")})"
            )
          )
      }
    }
    def description = "parser"
  }

  implicit val organizationProvider: ArgParser[OrganizationProvider.Value] = new ArgParser[OrganizationProvider.Value] {

    def apply(current: Option[OrganizationProvider.Value], index: Int, span: Int, v: String) = {
      val value = v.trim.toLowerCase
      OrganizationProvider.values.find(_.toString == value) match {
        case Some(provider) => Right(provider)
        case _ =>
          Left(
            Error.Other(
              s"${value} is an unsupported/unrecognized organization provider. (Available organization provider are: ${OrganizationProvider.values
                .mkString(",")})"
            )
          )
      }
    }
    def description = "organizationProvider"
  }
}
