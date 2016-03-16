name := "common"

organization := ApplicationBuild.appIssuer

version := ApplicationBuild.appVersion

ApplicationBuild.defaultSettings

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-mailer" % "2.4.1",
  "com.typesafe.akka" %% "akka-actor" % "2.3.14",
  "com.typesafe.akka" %% "akka-camel" % "2.3.14",
  "org.apache.activemq" % "activemq-camel" % "5.12.0",
  "commons-codec" % "commons-codec" % "1.10",
  "commons-lang" % "commons-lang" % "2.6",
  "com.google.zxing" % "core" % "3.2.0",
  "com.google.zxing" % "javase" % "3.2.0"
)
