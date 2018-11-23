name := "elastic4play"

organization := "org.thehive-project"

organizationName := "TheHive-Project"

organizationHomepage := Some(url("https://thehive-project.org/"))

licenses += "AGPL-V3" -> url("https://www.gnu.org/licenses/agpl-3.0.html")

lazy val elastic4play = (project in file("."))
  .enablePlugins(PlayScala, PlayAkkaHttp2Support)
// Add Http2 support to be able to ask client certificate
// cf. https://github.com/playframework/playframework/issues/8143

scalaVersion := "2.12.6"

resolvers += "elasticsearch-releases" at "https://artifacts.elastic.co/maven"

libraryDependencies ++= Seq(
  cacheApi,
  "com.sksamuel.elastic4s" %% "elastic4s-core" % "5.6.6",
  "com.sksamuel.elastic4s" %% "elastic4s-streams" % "5.6.6",
  "com.sksamuel.elastic4s" %% "elastic4s-tcp" % "5.6.6",
  "com.sksamuel.elastic4s" %% "elastic4s-xpack-security" % "5.6.6",
  "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.10" % Test,
  "org.scalactic" %% "scalactic" % "3.0.5",
  "org.bouncycastle" % "bcprov-jdk15on" % "1.58",
  "com.floragunn" % "search-guard-ssl" % "5.6.9-23",
  specs2 % Test
)

PlayKeys.externalizeResources := false

bintrayOrganization := Some("thehive-project-staging")

bintrayRepository := "maven"

publishMavenStyle := true

scalacOptions in ThisBuild ++= Seq(
  "-encoding", "UTF-8",
  "-deprecation", // warning and location for usages of deprecated APIs
  "-feature", // warning and location for usages of features that should be imported explicitly
  "-unchecked", // additional warnings where generated code depends on assumptions
  "-Xlint", // recommended additional warnings
  "-Ywarn-adapted-args", // Warn if an argument list is modified to match the receiver
  "-Ywarn-value-discard", // Warn when non-Unit expression results are unused
  "-Ywarn-inaccessible",
  "-Ywarn-dead-code"
)
