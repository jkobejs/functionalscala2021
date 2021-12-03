package io.github.jkobejs.app

import zio.{ App, ExitCode, Task, URIO, ZIO }
import zhttp.http._
import zhttp.service.Server
import zio.blocking.Blocking
import zio.nio.file.Files
import zio.nio.core.file.Path

import java.nio.charset.StandardCharsets

object Main extends App {
  val app: Http[Blocking, Throwable, Request, UResponse] = Http.collectM[Request] {
    case Method.GET -> Root / "login" / u / p =>
      for {
        username <- readSecret("username")
        password <- readSecret("password")
      } yield
        if (username.contains(u) && password.contains(p))
          Response.text("Functional Scala Rulez!")
        else
          Response.fromHttpError(HttpError.Unauthorized("Unauthorized"))
    case Method.GET -> Root / "health"        =>
      ZIO.succeed(Response.text("OK"))
  }

  private def readSecret(secret: String): ZIO[Blocking, Throwable, Option[String]] = {
    val path = (Path.apply("/etc", "secrets", secret))

    for {
      exists <- Files.exists(path)
      secret <- if (!exists) ZIO.none
                else
                  Files
                    .readAllBytes(path)
                    .flatMap(content => Task(new String(content.toArray, StandardCharsets.UTF_8)))
                    .map(Some(_))
    } yield secret

  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    val port = 9000
    (zio.console.putStrLn(s"Listening on $port") *>
      Server.start(port, app)).exitCode
  }
}
