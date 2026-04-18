package com.uic.cs553.distributed.io

import com.uic.cs553.distributed.model.SimGraph
import com.uic.cs553.distributed.runtime.{PdfEntry, SimulationConfig}
import io.circe.generic.auto.*
import io.circe.syntax.*

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

object EnrichedGraphExporter {
  final case class EnrichedNode(
    id: Int,
    storedValue: Double,
    valuableData: Boolean,
    pdf: Vector[PdfEntry],
    timerEnabled: Boolean,
    inputEnabled: Boolean
  )

  final case class EnrichedEdge(
    from: Int,
    to: Int,
    actionType: Int,
    cost: Double,
    allowedMessages: Set[String]
  )

  final case class EnrichedGraph(
    initialNodeId: Int,
    enabledAlgorithms: Vector[String],
    algorithmInitiatorNode: Int,
    nodes: Vector[EnrichedNode],
    edges: Vector[EnrichedEdge]
  )

  def write(graph: SimGraph, config: SimulationConfig, outDir: Path): Either[String, Unit] =
    try {
      Files.createDirectories(outDir)
      val enriched = build(graph, config)
      Files.writeString(outDir.resolve("enriched-graph.json"), enriched.asJson.spaces2, StandardCharsets.UTF_8)
      Files.writeString(outDir.resolve("enriched-graph.dot"), toDot(enriched), StandardCharsets.UTF_8)
      Right(())
    } catch {
      case ex: Exception => Left(s"Failed to export enriched graph: ${ex.getMessage}")
    }

  def build(graph: SimGraph, config: SimulationConfig): EnrichedGraph = {
    val nodes = graph.nodes.map { node =>
      EnrichedNode(
        id = node.id,
        storedValue = node.storedValue,
        valuableData = node.valuableData,
        pdf = config.pdfForNode(node.id),
        timerEnabled = config.timerFor(node.id).isDefined,
        inputEnabled = config.isInputNode(node.id)
      )
    }

    val edges = graph.edges.map { edge =>
      EnrichedEdge(
        from = edge.from,
        to = edge.to,
        actionType = edge.actionType,
        cost = edge.cost,
        allowedMessages = config.allowedMessagesFor(edge.from, edge.to)
      )
    }

    EnrichedGraph(
      initialNodeId = graph.initialNodeId,
      enabledAlgorithms = config.enabledAlgorithms,
      algorithmInitiatorNode = config.algorithmInitiatorNode,
      nodes = nodes,
      edges = edges
    )
  }

  private def toDot(graph: EnrichedGraph): String = {
    val nodes = graph.nodes.map { node =>
      val flags = Vector(
        Option.when(node.id == graph.initialNodeId)("initial"),
        Option.when(node.timerEnabled)("timer"),
        Option.when(node.inputEnabled)("input")
      ).flatten.mkString(",")
      s"""  ${node.id} [label="${node.id}${if flags.nonEmpty then s"\\n$flags" else ""}"];"""
    }

    val edges = graph.edges.map { edge =>
      val label = edge.allowedMessages.toVector.sorted.mkString(",")
      s"""  ${edge.from} -> ${edge.to} [label="$label"];"""
    }

    (Vector("digraph EnrichedGraph {") ++ nodes ++ edges ++ Vector("}")).mkString(System.lineSeparator())
  }
}
