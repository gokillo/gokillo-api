name := "gokillo-api"

organization := ApplicationBuild.appIssuer

version := ApplicationBuild.appVersion

ApplicationBuild.defaultSettings

lazy val common = project.in(file("modules/common")).enablePlugins(play.PlayScala).settings(
  javaOptions in Test += "-Dconfig.resource=common-application.conf"
)

lazy val apidocs = project.in(file("modules/apidocs")).enablePlugins(play.PlayScala, SbtWeb).settings(
  javaOptions in Test += "-Dconfig.resource=apidocs-application.conf"
).dependsOn(
  common % "test->test;compile->compile"
)

lazy val auth = project.in(file("modules/auth")).enablePlugins(play.PlayScala).settings(
  javaOptions in Test += "-Dconfig.resource=auth-application.conf"
).dependsOn(
  common % "test->test;compile->compile"
)

lazy val core = project.in(file("modules/core")).enablePlugins(play.PlayScala).settings(
  javaOptions in Test += "-Dconfig.resource=core-application.conf"
).dependsOn(
  common % "test->test;compile->compile",
  auth % "test->test;compile->compile",
  pay % "test->test;compile->compile"
)

lazy val media = project.in(file("modules/media")).enablePlugins(play.PlayScala).settings(
  javaOptions in Test += "-Dconfig.resource=media-application.conf"
).dependsOn(
  common % "test->test;compile->compile",
  auth % "test->test;compile->compile"
)

lazy val messaging = project.in(file("modules/messaging")).enablePlugins(play.PlayScala).settings(
  javaOptions in Test += "-Dconfig.resource=messaging-application.conf"
).dependsOn(
  common % "test->test;compile->compile",
  auth % "test->test;compile->compile"
)

lazy val pay = project.in(file("modules/pay")).enablePlugins(play.PlayScala).settings(
  javaOptions in Test += "-Dconfig.resource=pay-application.conf"
).dependsOn(
  common % "test->test;compile->compile",
  auth % "test->test;compile->compile"
)

lazy val `gokillo-api` = project.in(file(".")).enablePlugins(play.PlayScala).dependsOn(
  auth % "test->test;compile->compile",
  core % "test->test;compile->compile",
  media % "test->test;compile->compile",
  messaging % "test->test;compile->compile",
  pay % "test->test;compile->compile",
  apidocs % "test->test;compile->compile"
).aggregate(
  auth,
  core,
  media,
  messaging,
  pay,
  apidocs
)
