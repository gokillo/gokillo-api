name := "pay"

organization := ApplicationBuild.appIssuer

version := ApplicationBuild.appVersion

ApplicationBuild.defaultSettings

libraryDependencies ++= Seq(
  ws,
  "org.bitcoinj" % "bitcoinj-core" % "0.13.2",
  "org.iban4j" % "iban4j" % "3.2.0",
  "com.paypal.sdk" % "paypal-core" % "1.7.0",
  "com.paypal.sdk" % "rest-api-sdk" % "1.4.1"
)
