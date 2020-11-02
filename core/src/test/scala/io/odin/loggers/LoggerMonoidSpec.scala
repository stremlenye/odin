package io.odin.loggers

import java.util.UUID

import cats.data.WriterT
import cats.effect.{Clock, IO}
import cats.kernel.laws.discipline.MonoidTests
import cats.syntax.all._
import io.odin.{Level, Logger, LoggerMessage, OdinSpec}
import org.scalacheck.{Arbitrary, Gen}

class LoggerMonoidSpec extends OdinSpec {
  type F[A] = WriterT[IO, List[(UUID, LoggerMessage)], A]

  checkAll("Logger", MonoidTests[Logger[F]].monoid)

  it should "(logger1 |+| logger2).log <-> (logger1.log |+| logger2.log)" in {
    forAll { (uuid1: UUID, uuid2: UUID, msg: LoggerMessage) =>
      val logger1: Logger[F] = NamedLogger(uuid1)
      val logger2: Logger[F] = NamedLogger(uuid2)
      val a = (logger1 |+| logger2).log(msg)
      val b = logger1.log(msg) |+| logger2.log(msg)
      a.written.unsafeRunSync() shouldBe b.written.unsafeRunSync()
    }
  }

  it should "(logger1 |+| logger2).log(list) <-> (logger1.log(list) |+| logger2.log(list))" in {
    forAll { (uuid1: UUID, uuid2: UUID, msg: List[LoggerMessage]) =>
      val logger1: Logger[F] = NamedLogger(uuid1)
      val logger2: Logger[F] = NamedLogger(uuid2)
      val a = (logger1 |+| logger2).log(msg)
      val b = logger1.log(msg) |+| logger2.log(msg)
      a.written.unsafeRunSync() shouldBe b.written.unsafeRunSync()
    }
  }

  it should "set minimal level for underlying loggers" in {
    forAll { (uuid1: UUID, uuid2: UUID, level: Level, msg: List[LoggerMessage]) =>
      val logger1: Logger[F] = NamedLogger(uuid1)
      val logger2: Logger[F] = NamedLogger(uuid2)
      val a = (logger1 |+| logger2).withMinimalLevel(level).log(msg)
      val b = (logger1.withMinimalLevel(level) |+| logger2.withMinimalLevel(level)).log(msg)
      a.written.unsafeRunSync() shouldBe b.written.unsafeRunSync()
    }
  }

  it should "keep the minimal level for underlying loggers if not explicitly set" in {
    forAll { (uuid1: UUID, uuid2: UUID, level: Level, msg: List[LoggerMessage]) =>
      val logger1: Logger[F] = NamedLogger(uuid1)
      val logger2: Logger[F] = NamedLogger(uuid2)
      val a = (logger1.withMinimalLevel(level) |+| logger2).log(msg)
      val b = (logger1 |+| logger2.withMinimalLevel(level)).log(msg)
      a.written.unsafeRunSync() shouldBe b.written.unsafeRunSync()
    }
  }

  it should "keep the minimal level for underlying loggers if not explicitly set during the further compositions" in {
    forAll { (uuid1: UUID, uuid2: UUID, msg: List[LoggerMessage]) =>
      import io.odin.syntax._
      val logger1: Logger[F] = NamedLogger(uuid1).withMinimalLevel(Level.Error)
      val logger2: Logger[F] = NamedLogger(uuid2).withMinimalLevel(Level.Info)
      val infoMessages = msg.map(_.copy(level = Level.Error)) ++ msg.map(_.copy(level = Level.Info))
      val a = logger1
        .withConstContext(Map.empty)
        .log(infoMessages)
        .written
        .unsafeRunSync() ++
        logger2
          .withConstContext(Map.empty)
          .log(infoMessages)
          .written
          .unsafeRunSync()

      val composedLogger = (logger1 |+| logger2).withConstContext(Map.empty)

      val b = infoMessages.traverse(composedLogger.log)
      a shouldBe b.written.unsafeRunSync()
    }
  }

  case class NamedLogger(loggerId: UUID) extends DefaultLogger[F] {
    def log(msg: LoggerMessage): F[Unit] = WriterT.tell(List(loggerId -> msg))
  }

  implicit def clock: Clock[IO] = zeroClock

  implicit def arbitraryWriterLogger: Arbitrary[Logger[F]] = Arbitrary(
    Gen.uuid.map(NamedLogger)
  )
}
