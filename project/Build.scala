import sbt._
import Keys._

object ApplicationBuild extends Build {

  val appIssuer = "com.gokillo"

  /*
  val branch = "git rev-parse --abbrev-ref HEAD".!!.trim
  val commit = "git rev-parse --short HEAD".!!.trim
  val buildTime = (new java.text.SimpleDateFormat("yyyyMMdd-HHmmss")).format(new java.util.Date())
  val appVersion = "%s-%s-%s".format(branch, commit, buildTime)
  */
  val appVersion = "1.0"

  val defaultScalacOptions = Seq(
    "-unchecked", "-deprecation", "-feature", "-language:reflectiveCalls", "-language:implicitConversions",
    "-language:postfixOps", "-language:dynamics", "-language:higherKinds", "-language:existentials",
    "-language:experimental.macros", "-Xmax-classfile-name", "140", "-encoding", "UTF-8")

  val defaultResolvers = Seq(
    "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
    "pk11 repository" at "http://pk11-scratch.googlecode.com/svn/trunk",
    "Mandubian maven bintray" at "http://dl.bintray.com/mandubian/maven",
    Resolver.url("Gok!llo repository releases", url("http://repo.gokillo.com/releases"))(Resolver.ivyStylePatterns),
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots")
  )

  val defaultLibraryDependencies = Seq(
    "org.sedis" %% "sedis" % "1.2.2",
    "org.scalaz" %% "scalaz-core" % "7.1.3",
    "org.scalaz.stream" %% "scalaz-stream" % "0.7.2a",
    "com.typesafe.play.plugins" %% "play-plugins-redis" % "2.3.1",
    "com.typesafe.play" %% "play-cache" % "2.3.8",
    "com.google.guava" % "guava" % "18.0",
    "com.gokillo" %% "brix-corelib" % "0.1.0",
    "com.mandubian" %% "play-json-zipper" % "1.2",
    "org.reactivemongo" %% "play2-reactivemongo" % "0.10.5.0.akka23",
    "joda-time" % "joda-time" % "2.6",
    "org.joda" % "joda-convert" % "1.7",
    "com.wordnik" %% "swagger-play2" % "1.3.12",
    "com.wordnik" %% "swagger-play2-utils" % "1.3.12",
    "org.specs2" %% "specs2" % "3.0-M1" % "test",
    "org.slf4j" % "slf4j-nop" % "1.7.7" % "test"
  )

  val defaultSettings = Defaults.coreDefaultSettings ++ Seq(
    evictionWarningOptions in update := EvictionWarningOptions.default
      .withWarnTransitiveEvictions(false)
      .withWarnDirectEvictions(false)
      .withWarnScalaVersionEviction(false),
    scalaVersion := "2.11.6",
    scalacOptions ++= defaultScalacOptions,
    javacOptions ++= Seq(
      "-source", "1.8",
      "-target", "1.8"
    ),
    resolvers ++= defaultResolvers,
    libraryDependencies ++= defaultLibraryDependencies
  )
}
