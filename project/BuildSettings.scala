import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import sbt.Keys._
import sbt._

import scalariform.formatter.preferences._

object BuildSettings extends AutoPlugin {
  override def trigger = allRequirements

  override def projectSettings = Seq(
    organization := "org.cert-bdf",
    licenses += "AGPL-V3" -> url("https://www.gnu.org/licenses/agpl-3.0.html"),
    resolvers += Resolver.bintrayRepo("cert-bdf", "elastic4play"),
    scalaVersion := Dependencies.scalaVersion,
    libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.12",
    libraryDependencies ~= { _.map(_.exclude("org.slf4j", "slf4j-nop")) },
    scalacOptions ++= Seq(
      "-encoding", "UTF-8",
      "-deprecation", // Emit warning and location for usages of deprecated APIs.
      "-feature", // Emit warning and location for usages of features that should be imported explicitly.
      "-unchecked", // Enable additional warnings where generated code depends on assumptions.
      //"-Xfatal-warnings", // Fail the compilation if there are any warnings.
      "-Xlint", // Enable recommended additional warnings.
      "-Ywarn-adapted-args", // Warn if an argument list is modified to match the receiver.
      "-Ywarn-dead-code", // Warn when dead code is identified.
      "-Ywarn-inaccessible", // Warn about inaccessible types in method signatures.
      "-Ywarn-nullary-override", // Warn when non-nullary overrides nullary, e.g. def foo() over def foo.
      "-Ywarn-numeric-widen", // Warn when numerics are widened.
      "-Ywarn-value-discard", // Warn when non-Unit expression results are unused
      // "-Ylog-classpath",
      //"-Xlog-implicits",
      //      "-Yshow-trees-compact",
      //      "-Yshow-trees-stringified",
      //      "-Ymacro-debug-lite",
      "-Xlog-free-types",
      "-Xlog-free-terms",
      "-Xprint-types"),
    scalacOptions in Test ~= { (options: Seq[String]) ⇒
      options filterNot (_ == "-Ywarn-dead-code") // Allow dead code in tests (to support using mockito).
    },
    parallelExecution in Test := false,
    fork in Test := true,
    javaOptions ++= Seq(
      "-Xmx1G",
      "-Dlog4j.configurationFile=conf/log4j2.xml"),

    //SbtScalariform.defaultScalariformSettings,
    ScalariformKeys.preferences := ScalariformKeys.preferences.value
      .setPreference(AlignParameters, false)
      //  .setPreference(FirstParameterOnNewline, Force)
      .setPreference(AlignArguments, true)
      //  .setPreference(FirstArgumentOnNewline, true)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 60)
      .setPreference(CompactControlReadability, true)
      .setPreference(CompactStringConcatenation, false)
      .setPreference(DoubleIndentClassDeclaration, true)
      //  .setPreference(DoubleIndentMethodDeclaration, true)
      .setPreference(FormatXml, true)
      .setPreference(IndentLocalDefs, false)
      .setPreference(IndentPackageBlocks, false)
      .setPreference(IndentSpaces, 2)
      .setPreference(IndentWithTabs, false)
      .setPreference(MultilineScaladocCommentsStartOnFirstLine, false)
      //  .setPreference(NewlineAtEndOfFile, true)
      .setPreference(PlaceScaladocAsterisksBeneathSecondAsterisk, false)
      .setPreference(PreserveSpaceBeforeArguments, false)
      //  .setPreference(PreserveDanglingCloseParenthesis, false)
      .setPreference(DanglingCloseParenthesis, Prevent)
      .setPreference(RewriteArrowSymbols, true)
      .setPreference(SpaceBeforeColon, false)
      //  .setPreference(SpaceBeforeContextColon, false)
      .setPreference(SpaceInsideBrackets, false)
      .setPreference(SpaceInsideParentheses, false)
      .setPreference(SpacesWithinPatternBinders, true)
      .setPreference(SpacesAroundMultiImports, true))

}