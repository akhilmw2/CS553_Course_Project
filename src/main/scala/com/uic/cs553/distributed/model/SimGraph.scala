package com.uic.cs553.distributed.model

final case class SimNode(
  id: Int,
  storedValue: Double,
  valuableData: Boolean
)

final case class SimEdge(
  from: Int,
  to: Int,
  actionType: Int,
  cost: Double,
  allowedMessages: Set[String] = Set.empty
)

final case class SimGraph(
  initialNodeId: Int,
  nodes: Vector[SimNode],
  edges: Vector[SimEdge]
) {
  lazy val nodeIds: Set[Int] = nodes.iterator.map(_.id).toSet

  lazy val outgoingNeighbors: Map[Int, Vector[Int]] =
    edges
      .groupBy(_.from)
      .view
      .mapValues(_.iterator.map(_.to).toVector.sorted)
      .toMap
      .withDefaultValue(Vector.empty)

  lazy val incomingNeighbors: Map[Int, Vector[Int]] =
    edges
      .groupBy(_.to)
      .view
      .mapValues(_.iterator.map(_.from).toVector.sorted)
      .toMap
      .withDefaultValue(Vector.empty)

  def outNeighbors(nodeId: Int): Vector[Int] =
    outgoingNeighbors(nodeId)

  def inNeighbors(nodeId: Int): Vector[Int] =
    incomingNeighbors(nodeId)

  def edge(from: Int, to: Int): Option[SimEdge] =
    edges.find(edge => edge.from == from && edge.to == to)
}
