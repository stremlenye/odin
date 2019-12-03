package io.odin.loggers

import cats.data.WriterT
import cats.effect.{IO, Timer}
import cats.instances.list._
import cats.syntax.all._
import io.odin.{LoggerMessage, OdinSpec}
import io.odin.syntax._

class ContramapLoggerSpec extends OdinSpec {
  type F[A] = WriterT[IO, List[LoggerMessage], A]
  implicit val timer: Timer[IO] = zeroTimer

  checkAll("ContramapLogger", LoggerTests[F](new WriterTLogger[IO].contramap(identity), _.written.unsafeRunSync()).all)

  it should "contramap(identity).log(msg) <-> log(msg)" in {
    val logger = new WriterTLogger[IO].contramap(identity)
    forAll { msgs: List[LoggerMessage] =>
      val written = msgs.traverse(logger.log).written.unsafeRunSync()
      val batchWritten = logger.log(msgs).written.unsafeRunSync()

      written shouldBe msgs
      batchWritten shouldBe written
    }
  }

  it should "contramap(f).log(msg) <-> log(f(msg))" in {
    forAll { (msgs: List[LoggerMessage], fn: LoggerMessage => LoggerMessage) =>
      val logger = new WriterTLogger[IO].contramap(fn)
      val written = msgs.traverse(logger.log).written.unsafeRunSync()
      val batchWritten = logger.log(msgs).written.unsafeRunSync()

      written shouldBe msgs.map(fn)
      batchWritten shouldBe written
    }
  }
}
