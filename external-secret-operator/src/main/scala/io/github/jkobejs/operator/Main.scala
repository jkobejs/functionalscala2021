package io.github.jkobejs.operator

import com.coralogix.zio.k8s.client.apiextensions.v1.customresourcedefinitions
import com.coralogix.zio.k8s.client.config.httpclient.k8sDefault
import com.coralogix.zio.k8s.client.io.github.jkobejs.definitions.externalsecret.v1.ExternalSecret
import com.coralogix.zio.k8s.client.io.github.jkobejs.v1.externalsecrets
import com.coralogix.zio.k8s.client.v1.secrets.Secrets
import com.coralogix.zio.k8s.operator.Registration
import io.github.jkobejs.operator.externalsecret.ExternalSecretOperator
import io.github.vigoo.zioaws.{ core, netty, secretsmanager }
import zhttp.http._
import zhttp.service.Server
import zio.console.Console
import zio.logging.slf4j.bridge.initializeSlf4jBridge
import zio.logging.{ log, LogLevel, Logging }
import zio.magic._
import zio.{ App, ExitCode, URIO }

object Main extends App {
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    ((for {
      _        <- Registration.registerIfMissing[ExternalSecret](externalsecrets.customResourceDefinition)
      operator <- ExternalSecretOperator.operator
      _        <- operator.start() <&> ExternalSecretOperator.syncer
    } yield ()) <&> healthServer)
      .injectSome[zio.ZEnv](
        netty.default,
        core.config.default,
        secretsmanager.live,
        k8sDefault,
        Logging.consoleErr(logLevel = LogLevel.Info) >>> initializeSlf4jBridge,
        Secrets.live,
        customresourcedefinitions.CustomResourceDefinitions.live,
        externalsecrets.ExternalSecrets.live
      )
      .exitCode

  val healthServer: URIO[Logging with Console, ExitCode] = {
    val port = 9000
    (log.info(s"Listening on $port") *>
      Server.start(port, Http.collect[Request] { case Method.GET -> Root / "health" => Response.text("OK") })).exitCode
  }
}
