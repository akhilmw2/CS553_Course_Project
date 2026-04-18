package com.uic.cs553.distributed.runtime

import com.uic.cs553.distributed.model.SimGraph

final case class TopologyValidation(
  warnings: Vector[String],
  errors: Vector[String]
)

object TopologyValidator {
  def validate(graph: SimGraph, config: SimulationConfig): TopologyValidation = {
    val normalizedAlgorithms = config.enabledAlgorithms.map(_.trim.toLowerCase).distinct
    val initiatorErrors =
      if (graph.nodeIds.contains(config.algorithmInitiatorNode)) Vector.empty
      else Vector(s"Configured algorithm initiator node ${config.algorithmInitiatorNode} is not present in the graph")

    val echoErrors =
      if (normalizedAlgorithms.contains("echo")) validateEcho(graph, config)
      else Vector.empty

    val waveWarnings =
      if (normalizedAlgorithms.contains("wave") && graph.outNeighbors(config.algorithmInitiatorNode).isEmpty)
        Vector(s"Wave initiator node ${config.algorithmInitiatorNode} has no outgoing neighbors")
      else Vector.empty

    TopologyValidation(
      warnings = waveWarnings,
      errors = initiatorErrors ++ echoErrors
    )
  }

  private def validateEcho(graph: SimGraph, config: SimulationConfig): Vector[String] =
    graph.edges.flatMap { edge =>
      val forwardAllowsControl = config.allowedMessagesFor(edge.from, edge.to).contains("CONTROL")
      val reverseAllowsControl = graph.edge(edge.to, edge.from).exists(reverse => config.allowedMessagesFor(reverse.from, reverse.to).contains("CONTROL"))

      if (forwardAllowsControl && !reverseAllowsControl) {
        Some(s"Echo requires a reverse CONTROL path for edge ${edge.from} -> ${edge.to}, but none exists")
      } else {
        None
      }
    }
}
