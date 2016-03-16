name := "auth"

organization := ApplicationBuild.appIssuer

version := ApplicationBuild.appVersion

ApplicationBuild.defaultSettings

libraryDependencies ++= Seq(
  "com.nimbusds" % "nimbus-jose-jwt" % "3.4"
)
