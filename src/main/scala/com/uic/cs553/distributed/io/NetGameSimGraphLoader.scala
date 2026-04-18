package com.uic.cs553.distributed.io

import com.uic.cs553.distributed.model.{SimEdge, SimGraph, SimNode}
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.parser.decode

import java.nio.file.{Files, Path}
import scala.util.Try

object NetGameSimGraphLoader {

  private final case class NetGameSimNode(
    id: Int,
    storedValue: Double,
    valuableData: Boolean
  )

  private object NetGameSimNode {
    implicit val decoder: Decoder[NetGameSimNode] = deriveDecoder
  }

  private final case class NetGameSimEdge(
    actionType: Int,
    fromNode: NetGameSimNode,
    toNode: NetGameSimNode,
    cost: Double
  )

  private object NetGameSimEdge {
    implicit val decoder: Decoder[NetGameSimEdge] = deriveDecoder
  }

  def load(path: Path): Either[String, SimGraph] =
    if (!Files.exists(path)) {
      Left(s"NetGameSim graph export does not exist: $path")
    } else {
      val lines = Try(Files.readAllLines(path)).toEither.left.map(_.getMessage).map(_.toArray(new Array[String](0)).toVector)
      lines.flatMap(parseLines)
    }

  private def parseLines(lines: Vector[String]): Either[String, SimGraph] =
    lines.filter(_.trim.nonEmpty) match {
      case nodeLine +: edgeLine +: _ =>
        for {
          nodes <- decode[Set[NetGameSimNode]](nodeLine).left.map(_.getMessage)
          edges <- decode[List[NetGameSimEdge]](edgeLine).left.map(_.getMessage)
          graph <- buildGraph(nodes.toVector, edges.toVector)
        } yield graph
      case _ =>
        Left("NetGameSim JSON export must contain a node line followed by an edge line")
    }

  private def buildGraph(
    sourceNodes: Vector[NetGameSimNode],
    sourceEdges: Vector[NetGameSimEdge]
  ): Either[String, SimGraph] = {
    val nodes = sourceNodes
      .map(node => SimNode(node.id, node.storedValue, node.valuableData))
      .sortBy(_.id)
    val nodeIds = nodes.iterator.map(_.id).toSet
    val invalidEdges = sourceEdges.filterNot(edge => nodeIds.contains(edge.fromNode.id) && nodeIds.contains(edge.toNode.id))

    if (nodes.isEmpty) {
      Left("NetGameSim graph export does not contain any nodes")
    } else if (invalidEdges.nonEmpty) {
      Left(s"NetGameSim graph export contains edges that reference missing nodes: ${invalidEdges.take(3).mkString(", ")}")
    } else {
      val edges = sourceEdges
        .map(edge => SimEdge(edge.fromNode.id, edge.toNode.id, edge.actionType, edge.cost))
        .sortBy(edge => (edge.from, edge.to))

      Right(
        SimGraph(
          initialNodeId = if (nodeIds.contains(0)) 0 else nodes.head.id,
          nodes = nodes,
          edges = edges
        )
      )
    }
  }
}
