package io.github.jkobejs.operator.externalsecret

import com.coralogix.zio.k8s.client.K8sFailure
import com.coralogix.zio.k8s.client.io.github.jkobejs.definitions.externalsecret.v1.ExternalSecret
import com.coralogix.zio.k8s.client.io.github.jkobejs.v1.externalsecrets
import com.coralogix.zio.k8s.client.io.github.jkobejs.v1.externalsecrets.ExternalSecrets
import com.coralogix.zio.k8s.client.model._
import com.coralogix.zio.k8s.client.v1.secrets
import com.coralogix.zio.k8s.client.v1.secrets.Secrets
import com.coralogix.zio.k8s.model.core.v1.Secret
import com.coralogix.zio.k8s.model.pkg.apis.meta.v1.{ DeleteOptions, ObjectMeta }
import com.coralogix.zio.k8s.operator.Operator._
import com.coralogix.zio.k8s.operator._
import com.coralogix.zio.k8s.operator.aspects.logEvents
import com.softwaremill.quicklens._
import io.github.vigoo.zioaws.core.AwsError
import io.github.vigoo.zioaws.secretsmanager
import io.github.vigoo.zioaws.secretsmanager.SecretsManager
import io.github.vigoo.zioaws.secretsmanager.model.GetSecretValueRequest
import io.github.vigoo.zioaws.secretsmanager.model.primitives.SecretStringType
import zio._
import zio.clock.Clock
import zio.json._
import zio.logging.{ log, Logging }

import java.time.{ DateTimeException, Duration }

object ExternalSecretOperator {

  sealed trait SecretFailures extends Throwable

  object SecretFailures {
    final case class K8(failure: K8sFailure) extends SecretFailures
    final case class Aws(error: AwsError) extends SecretFailures
    final case class Invalid(error: String) extends SecretFailures
    final case class Date(exception: DateTimeException) extends SecretFailures
  }

  val eventProcessor: EventProcessor[
    ExternalSecrets with SecretsManager with Secrets with Clock with Logging,
    SecretFailures,
    ExternalSecret
  ] =
    (_, event) =>
      event match {
        case Reseted()     =>
          ZIO.unit
        case Added(item)   =>
          (for {
            name      <- item.getName.mapError(SecretFailures.K8)
            _         <- log.info(s"External secret ${name} is added")
            namespace <- item.getMetadata.flatMap(_.getNamespace).mapBoth(SecretFailures.K8, K8sNamespace(_))
            secret    <- getAwsSecret(name)
            _         <- createK8Secret(name, namespace, secret)
            _         <- updateStatus(item, namespace)
          } yield ()).mapError(OperatorError.apply)
        case Modified(_)   =>
          ZIO.unit
        case Deleted(item) =>
          (for {
            secretName <- item.getName.mapError(SecretFailures.K8)
            _          <- log.info(s"External secret ${secretName} is deleted")
            namespace  <- item.getMetadata.flatMap(_.getNamespace).mapBoth(SecretFailures.K8, K8sNamespace(_))
            _          <- secrets.delete(secretName, DeleteOptions(), namespace).mapError(SecretFailures.K8)
          } yield ()).mapError(OperatorError.apply)

      }

  private def getAwsSecret(secretName: String): ZIO[SecretsManager, SecretFailures.Aws, SecretStringType] =
    for {
      awsSecret    <-
        secretsmanager.getSecretValue(GetSecretValueRequest(secretId = secretName)).mapError(SecretFailures.Aws)
      secretString <- awsSecret.secretString.mapError(SecretFailures.Aws)
    } yield secretString

  private def createK8Secret(
    name: String,
    namespace: K8sNamespace,
    secret: String
  ): ZIO[Secrets, SecretFailures, Unit] =
    for {
      secretMap <- ZIO
                     .fromEither(secret.fromJson[Map[String, String]])
                     .mapError(SecretFailures.Invalid)
      secret     = Secret(
                     stringData = secretMap,
                     metadata = ObjectMeta(
                       name = name,
                       namespace = namespace.value
                     )
                   )
      _         <- secrets.create(secret, namespace).mapError(SecretFailures.K8)
    } yield ()

  private def updateStatus(
    item: ExternalSecret,
    namespace: K8sNamespace
  ): ZIO[ExternalSecrets with Clock, SecretFailures, Unit] =
    for {
      lastSync <- clock.currentDateTime.mapError(SecretFailures.Date)
      _        <- externalsecrets
                    .replaceStatus(
                      item,
                      ExternalSecret.Status(
                        lastSync = lastSync,
                        state = ExternalSecret.Status.State.Synced
                      ),
                      namespace
                    )
                    .mapError(SecretFailures.K8)
    } yield ()

  val operator: ZIO[ExternalSecrets, Nothing, Operator[
    ExternalSecrets with SecretsManager with Secrets with Logging with Clock,
    SecretFailures,
    ExternalSecret
  ]] =
    ZIO
      .service[ExternalSecrets.Service]
      .flatMap(es =>
        Operator
          .namespaced(
            eventProcessor @@ logEvents
          )(namespace = None, buffer = 1024)
          .provide(es.asGeneric)
      )

  private def replaceK8Secret(
    name: String,
    namespace: K8sNamespace,
    secret: String
  ): ZIO[Secrets, SecretFailures, Unit] =
    for {
      secretMap        <- ZIO
                            .fromEither(secret.fromJson[Map[String, String]])
                            .mapError(SecretFailures.Invalid)
      kubernetesSecret <- secrets.get(name, namespace).mapError(SecretFailures.K8)
      _                <- secrets
                            .replace(
                              name,
                              kubernetesSecret.modify(_.stringData).setTo(secretMap),
                              namespace
                            )
                            .mapError(SecretFailures.K8)
    } yield ()

  val syncer: ZIO[ExternalSecrets with Clock with Secrets with SecretsManager with Logging, SecretFailures, Nothing] =
    (log.info("Syncing...")
      *>
        externalsecrets
          .getAll(namespace = None)
          .mapError(SecretFailures.K8)
          .mapM { item =>
            for {
              name      <- item.getName.mapError(SecretFailures.K8)
              namespace <- item.getMetadata.flatMap(_.getNamespace).mapBoth(SecretFailures.K8, K8sNamespace(_))
              awsSecret <- getAwsSecret(name)
              _         <- replaceK8Secret(name, namespace, awsSecret)
              _         <- updateStatus(item, namespace)
            } yield ()
          }
          .runDrain)
      .delay(Duration.ofSeconds(5))
      .forever

}
