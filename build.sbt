ThisBuild / scalaVersion := "2.13.6"
ThisBuild / organization := "io.jkobejs.operators"

lazy val operator = (project in file("external-secret-operator"))
  .settings(
    name                              := "external-secret-operator",
    libraryDependencies ++= Seq(
      "dev.zio"                       %% "zio"                      % Version.zio,
      "io.d11"                        %% "zhttp"                    % Version.zioHttp,
      "dev.zio"                       %% "zio-logging"              % Version.zioLogging,
      "dev.zio"                       %% "zio-logging-slf4j-bridge" % Version.zioLogging,
      "com.coralogix"                 %% "zio-k8s-operator"         % Version.k8s,
      "com.coralogix"                 %% "zio-k8s-client-quicklens" % Version.k8s,
      "com.softwaremill.sttp.client3" %% "httpclient-backend-zio"   % Version.sttp,
      "com.softwaremill.sttp.client3" %% "slf4j-backend"            % Version.sttp,
      "io.github.kitlangton"          %% "zio-magic"                % Version.zioMagic,
      "io.github.vigoo"               %% "zio-aws-netty"            % Version.zioAws,
      "io.github.vigoo"               %% "zio-aws-secretsmanager"   % Version.zioAws,
      "dev.zio"                       %% "zio-json"                 % Version.zioJson,
      "dev.zio"                       %% "zio-test"                 % Version.zio % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    scalafmtOnCompile                 := true,
    version                           := "0.6.0-SNAPSHOT",
    externalCustomResourceDefinitions := Seq(
      file("crds/externalsecrets.yaml")
    )
  )
  .settings(sharedSettings)
  .enablePlugins(JavaAppPackaging, DockerPlugin, K8sCustomResourceCodegenPlugin)

lazy val loginApp = (project in file("login-app"))
  .settings(
    name    := "login-app",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio"     % Version.zio,
      "io.d11"  %% "zhttp"   % Version.zioHttp,
      "dev.zio" %% "zio-nio" % Version.zioNio
    ),
    version := "0.1.0-SNAPSHOT"
  )
  .settings(sharedSettings)
  .enablePlugins(JavaAppPackaging, DockerPlugin)

lazy val sharedSettings = Seq(
  dockerBaseImage := "openjdk:11"
)
