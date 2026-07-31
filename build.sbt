ThisBuild / scalaVersion := "3.3.6"
ThisBuild / version      := "0.1.0-SNAPSHOT"

lazy val microservice = Project("hmrc-log-dashboard-frontend", file("."))
  .enablePlugins(PlayScala)
  .settings(
    PlayKeys.playDefaultPort := 9250,
    libraryDependencies ++= AppDependencies(),
    resolvers += MavenRepository("HMRC-open-artefacts-maven2", "https://open.artefacts.tax.service.gov.uk/maven2"),
    TwirlKeys.templateImports ++= Seq(
      "uk.gov.hmrc.govukfrontend.views.html.components._",
      "uk.gov.hmrc.hmrcfrontend.views.html.components._",
      "uk.gov.hmrc.hmrcfrontend.views.html.helpers._",
      "uk.gov.hmrc.hmrcfrontend.views.viewmodels.hmrcstandardpage._",
      "views.html.helper.CSPNonce"
    ),
    routesImport ++= Seq("java.util.UUID"),
    scalacOptions ++= Seq(
      "-feature",
      "-Wconf:src=routes/.*:s",
      "-Wconf:msg=unused import&src=html/.*:s"
    )
  )
