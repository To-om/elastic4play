import Dependencies.Library

name := "elastic4play"

organization := "org.cert-bdf"

organizationName := "CERT-BDF"

organizationHomepage := Some(url("https://thehive-project.org/"))

licenses += "AGPL-V3" -> url("https://www.gnu.org/licenses/agpl-3.0.html")

//logLevel := Level.Debug

lazy val elastic4play = (project in file("core"))
  .enablePlugins(PlayScala)
  .disablePlugins(PlayLogback)
  .settings(libraryDependencies ++= Seq(
    Dependencies.Library.Play.cache,
    Dependencies.Library.scalaGuice,
    Dependencies.Library.Elastic4s.core,
    Dependencies.Library.Elastic4s.streams,
    Dependencies.Library.scalactic,
    Dependencies.Library.shapeless,
    Dependencies.Library.scalaCompiler,
    Dependencies.Library.Play.specs2 % Test))


lazy val elastic4playTest = (project in file("."))
  .enablePlugins(PlayScala)
  .disablePlugins(PlayLogback)
  .aggregate(elastic4play)
  .dependsOn(elastic4play)
  .dependsOn(elastic4play % "test->test")
  .settings(libraryDependencies ++= Seq(
    Library.Play.specs2 % Test,
    Library.Elastic4s.testkit))

PlayKeys.externalizeResources := false

bintrayOrganization := Some("cert-bdf")

bintrayRepository := "elastic4play"

publishMavenStyle := true
