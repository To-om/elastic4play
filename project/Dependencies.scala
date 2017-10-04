import sbt._

object Dependencies {
  val scalaVersion = "2.12.3"

  object Library {

    object Play {
      val version = play.core.PlayVersion.current
      val akkaVersion = "2.5.1"
      val ws = "com.typesafe.play" %% "play-ws" % version
      val cache = "com.typesafe.play" %% "play-cache" % version
      val json = "com.typesafe.play" %% "play-json" % version
      val test = "com.typesafe.play" %% "play-test" % version
      val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
      val specs2 = "com.typesafe.play" %% "play-specs2" % version
      val guice = "com.typesafe.play" %% "play-guice" % version
      object Specs2 {
        private val version = "3.6.6"
        val matcherExtra = "org.specs2" %% "specs2-matcher-extra" % version
        val mock = "org.specs2" %% "specs2-mock" % version
      }
    }

    //    object Specs2 {
    //      private val version = "3.6.6"
    //      val core = "org.specs2" %% "specs2-core" % version
    //      val matcherExtra = "org.specs2" %% "specs2-matcher-extra" % version
    //      val mock = "org.specs2" %% "specs2-mock" % version
    //    }
    val scalaGuice = "net.codingwell" %% "scala-guice" % "4.1.0"
    val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % "2.4.7"
    val reflections = "org.reflections" % "reflections" % "0.9.11"
    val zip4j = "net.lingala.zip4j" % "zip4j" % "1.3.2"
    val akkaTest = "com.typesafe.akka" %% "akka-stream-testkit" % "2.4.4"
    val shapeless = "com.chuusai" %% "shapeless" % "2.3.2"
    val scalactic = "org.scalactic" %% "scalactic" % "3.0.3"
    val scalaCompiler = "org.scala-lang" % "scala-compiler" % scalaVersion

    object Elastic4s {
      private val version = "5.4.9"
      val core = "com.sksamuel.elastic4s" %% "elastic4s-core" % version
      val streams = "com.sksamuel.elastic4s" %% "elastic4s-streams" % version
      val testkit = "com.sksamuel.elastic4s" %% "elastic4s-testkit" % version
    }
  }
}
