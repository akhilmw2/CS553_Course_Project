package com.uic.cs553.distributed.cli

import com.uic.cs553.distributed.io.{EnrichedGraphExporter, NetGameSimGraphLoader}
import com.uic.cs553.distributed.runtime.{InjectionFileReader, InteractiveInputRunner, SimulationBootstrap, SimulationConfig, TopologyValidator}

import java.nio.file.Paths
import scala.concurrent.Await
import scala.concurrent.duration.*

object SimMain {
  def main(args: Array[String]): Unit = {
    val parsed = parseArgs(args.toVector)

    val result = for {
      graphPath <- parsed.get("graph").toRight("Missing required argument --graph <path>")
      config <- parsed.get("config") match {
        case Some(path) => SimulationConfig.load(path)
        case None       => SimulationConfig.load()
      }
      graph <- NetGameSimGraphLoader.load(Paths.get(graphPath))
      injections <- parsed.get("inject") match {
        case Some(path) => InjectionFileReader.read(Paths.get(path))
        case None       => Right(Vector.empty)
      }
    } yield (graph, config, injections)

    result match {
      case Left(error) =>
        System.err.println(s"Failed to start simulator: $error")
        System.exit(1)

      case Right((graph, config, injections)) =>
        val validation = TopologyValidator.validate(graph, config)
        validation.warnings.foreach(warning => System.err.println(s"Topology warning: $warning"))
        if (validation.errors.nonEmpty) {
          validation.errors.foreach(error => System.err.println(s"Topology error: $error"))
          System.exit(1)
        }
        parsed.get("out").foreach { outDir =>
          EnrichedGraphExporter.write(graph, config, Paths.get(outDir)) match {
            case Left(error) =>
              System.err.println(error)
              System.exit(1)
            case Right(_) =>
              println(s"Exported enriched graph artifacts to $outDir")
          }
        }
        val handle = SimulationBootstrap.run(graph, config, injections)
        if (parsed.contains("interactive")) {
          InteractiveInputRunner.start(handle)
        }
        Await.result(handle.system.whenTerminated, config.duration + 5.seconds)
    }
  }

  private def parseArgs(args: Vector[String]): Map[String, String] =
    def loop(remaining: Vector[String], parsed: Map[String, String]): Map[String, String] =
      remaining match {
        case flag +: value +: tail if flag.startsWith("--") && !value.startsWith("--") =>
          loop(tail, parsed + (flag.stripPrefix("--") -> value))
        case flag +: tail if flag.startsWith("--") =>
          loop(tail, parsed + (flag.stripPrefix("--") -> "true"))
        case _ +: tail =>
          loop(tail, parsed)
        case _ =>
          parsed
      }

    loop(args, Map.empty)
}
