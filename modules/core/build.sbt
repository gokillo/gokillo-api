name := "core"

organization := ApplicationBuild.appIssuer

version := ApplicationBuild.appVersion

ApplicationBuild.defaultSettings

libraryDependencies ++= Seq(
  "org.apache.mahout" % "mahout-core" % "0.9",
  "org.apache.hadoop" % "hadoop-core" % "1.2.1"
)
