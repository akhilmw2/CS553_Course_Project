package com.uic.cs553.distributed.runtime

import scala.concurrent.Future
import scala.io.StdIn

object InteractiveInputRunner {
  def start(handle: SimulationHandle): Unit = {
    given scala.concurrent.ExecutionContext = handle.system.dispatcher
    Future {
      println()
      println("Interactive injection mode is active.")
      println("Commands: inject <nodeId> <kind> <payload>")
      println("Example:  inject 0 WORK hello")
      println("Exit:     quit")
      Iterator
        .continually(StdIn.readLine("sim> "))
        .takeWhile(_ != null)
        .takeWhile(processLine(_, handle))
        .foreach(_ => ())
    }
  }

  private def processLine(line: String, handle: SimulationHandle): Boolean = {
    val trimmed = line.trim
    if (trimmed.nonEmpty) {
      trimmed.split("\\s+", 4).toVector match {
        case Vector("quit") =>
          handle.metrics ! MetricsActor.PrintSummary
          handle.system.log.info("Interactive mode requested simulation shutdown")
          handle.system.terminate()
          false
        case Vector("inject", nodeIdValue, kind, payload) =>
          val nodeId = nodeIdValue.toInt
          handle.nodeRefs.get(nodeId) match {
            case Some(nodeRef) => nodeRef ! NodeActor.ExternalInput(kind, payload)
            case None          => println(s"No node with id $nodeId")
          }
          true
        case _ =>
          println("Invalid command. Use: inject <nodeId> <kind> <payload>")
          true
      }
    } else {
      true
    }
  }
}
