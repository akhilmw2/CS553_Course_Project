package com.uic.cs553.distributed.runtime

import akka.actor.{Actor, ActorLogging, Props}

object MetricsActor {
  sealed trait Command
  final case class MessageSent(from: Int, to: Int, kind: String) extends Command
  final case class MessageReceived(nodeId: Int, from: Int, kind: String) extends Command
  final case class MessageDropped(nodeId: Int, kind: String, reason: String) extends Command
  final case class AlgorithmEvent(algorithm: String, nodeId: Int, event: String) extends Command
  case object PrintSummary extends Command

  def props(): Props =
    Props(new MetricsActor)

  private final class MetricsActor extends Actor with ActorLogging {
    private var sentByType: Map[String, Int] = Map.empty
    private var receivedByType: Map[String, Int] = Map.empty
    private var droppedByReason: Map[String, Int] = Map.empty
    private var algorithmEvents: Map[String, Int] = Map.empty

    override def receive: Receive = {
      case MessageSent(_, _, kind) =>
        sentByType = increment(sentByType, kind)

      case MessageReceived(_, _, kind) =>
        receivedByType = increment(receivedByType, kind)

      case MessageDropped(_, _, reason) =>
        droppedByReason = increment(droppedByReason, reason)

      case AlgorithmEvent(algorithm, _, event) =>
        algorithmEvents = increment(algorithmEvents, s"$algorithm.$event")

      case PrintSummary =>
        log.info(
          "Simulation summary: sent={} received={} dropped={} algorithmEvents={}",
          sentByType,
          receivedByType,
          droppedByReason,
          algorithmEvents
        )
    }
  }

  private def increment(values: Map[String, Int], key: String): Map[String, Int] =
    values.updated(key, values.getOrElse(key, 0) + 1)
}
