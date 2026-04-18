package com.uic.cs553.distributed.runtime

import java.nio.file.{Files, Path}

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

final case class InjectionEvent(
  at: FiniteDuration,
  node: Int,
  kind: String,
  payload: String
)

object InjectionFileReader {
  def read(path: Path): Either[String, Vector[InjectionEvent]] =
    if (!Files.exists(path)) {
      Left(s"Injection file does not exist: $path")
    } else {
      val lines = Files.readAllLines(path).asScala.toVector
      lines.zipWithIndex.foldLeft(Right(Vector.empty): Either[String, Vector[InjectionEvent]]) {
        case (acc, (line, index)) =>
          acc.flatMap(events => parseLine(line, index + 1).map(event => events :+ event))
      }
    }

  private def parseLine(line: String, lineNumber: Int): Either[String, InjectionEvent] = {
    val trimmed = line.trim
    if (trimmed.isEmpty || trimmed.startsWith("#")) {
      Right(InjectionEvent(Duration.Zero, -1, "", ""))
    } else {
      trimmed.split("\\s+", 4).toList match {
        case delayMs :: node :: kind :: payload :: Nil =>
          Right(InjectionEvent(delayMs.toLong.millis, node.toInt, kind, payload))
        case _ =>
          Left(s"Invalid injection line $lineNumber: $line")
      }
    }
  }
}
