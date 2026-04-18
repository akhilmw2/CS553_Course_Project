package com.uic.cs553.distributed.runtime

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Timers}

import scala.util.Random

object NodeActor {
  sealed trait Command

  final case class Init(
    neighbors: Map[Int, ActorRef],
    allowedOnEdge: Map[Int, Set[String]],
    pdf: Vector[PdfEntry],
    timer: Option[TimerInitiator],
    acceptsExternalInput: Boolean,
    metrics: ActorRef,
    algorithms: Vector[DistributedAlgorithm]
  ) extends Command

  final case class ExternalInput(kind: String, payload: String) extends Command
  final case class Envelope(from: Int, kind: String, payload: String) extends Command
  private case object Tick extends Command

  def props(id: Int, seed: Long): Props =
    Props(new NodeActor(id, seed))
}

final class NodeActor(id: Int, seed: Long) extends Actor with ActorLogging with Timers {
  import NodeActor.*

  private val rng = new Random(seed)

  override def receive: Receive = waitingForInit

  private def waitingForInit: Receive = {
    case Init(neighbors, allowedOnEdge, pdf, timer, acceptsExternalInput, metrics, algorithms) =>
      timer.foreach(startTimer)
      val nodeContext = buildNodeContext(neighbors, allowedOnEdge, metrics)
      algorithms.foreach(_.onStart(nodeContext))
      context.become(active(neighbors, allowedOnEdge, pdf, timer, acceptsExternalInput, metrics, algorithms))
  }

  private def active(
    neighbors: Map[Int, ActorRef],
    allowedOnEdge: Map[Int, Set[String]],
    pdf: Vector[PdfEntry],
    timer: Option[TimerInitiator],
    acceptsExternalInput: Boolean,
    metrics: ActorRef,
    algorithms: Vector[DistributedAlgorithm]
  ): Receive = {
    case Tick =>
      val msgKind = timer.flatMap(fixedMessageFor).getOrElse(sampleFromPdf(pdf))
      sendToOneNeighbor(neighbors, allowedOnEdge, msgKind, s"tick-from-$id", metrics)
      val nodeContext = buildNodeContext(neighbors, allowedOnEdge, metrics)
      algorithms.foreach(_.onTick(nodeContext))

    case ExternalInput(kind, payload) =>
      if (acceptsExternalInput) {
        sendToOneNeighbor(neighbors, allowedOnEdge, kind, payload, metrics)
      } else {
        log.warning("Node {} rejected external input of type {} because it is not configured as an input node", id, kind)
        metrics ! MetricsActor.MessageDropped(id, kind, "external-input-disabled")
      }

    case Envelope(from, kind, payload) =>
      log.info("Node {} received {} from {} with payload={}", id, kind, from, payload)
      metrics ! MetricsActor.MessageReceived(id, from, kind)
      val nodeContext = buildNodeContext(neighbors, allowedOnEdge, metrics)
      val envelope = RuntimeEnvelope(from, kind, payload)
      algorithms.foreach(_.onMessage(nodeContext, envelope))

    case _: Init =>
  }

  private def buildNodeContext(
    neighbors: Map[Int, ActorRef],
    allowedOnEdge: Map[Int, Set[String]],
    metrics: ActorRef
  ): NodeContext =
    new NodeContext {
      override def nodeId: Int = id

      override def outgoingNeighbors: Vector[Int] =
        neighbors.keys.toVector.sorted

      override def send(to: Int, kind: String, payload: String): Unit =
        if (!neighbors.contains(to)) {
          log.warning("Node {} cannot send {} to missing neighbor {}", id, kind, to)
          metrics ! MetricsActor.MessageDropped(id, kind, "missing-neighbor")
        } else if (!allowedOnEdge.getOrElse(to, Set.empty).contains(kind)) {
          log.warning("Node {} cannot send {} to {} because the edge does not allow it", id, kind, to)
          metrics ! MetricsActor.MessageDropped(id, kind, "edge-disallowed")
        } else {
          neighbors(to) ! Envelope(from = id, kind = kind, payload = payload)
          metrics ! MetricsActor.MessageSent(id, to, kind)
        }

      override def sendToAll(kind: String, payload: String, except: Set[Int]): Unit =
        outgoingNeighbors.filterNot(except.contains).foreach(send(_, kind, payload))

      override def emitAlgorithmEvent(algorithm: String, event: String): Unit =
        metrics ! MetricsActor.AlgorithmEvent(algorithm, id, event)

      override def logInfo(message: String): Unit =
        log.info(message)

      override def logWarning(message: String): Unit =
        log.warning(message)
    }

  private def startTimer(timer: TimerInitiator): Unit =
    timers.startTimerAtFixedRate("tick", Tick, timer.tickEvery)

  private def fixedMessageFor(timer: TimerInitiator): Option[String] =
    if (timer.mode == "fixed") timer.fixedMsg else None

  private def sampleFromPdf(pdf: Vector[PdfEntry]): String = {
    val threshold = rng.nextDouble()
    pdf.foldLeft((Option.empty[String], 0.0d)) { case ((selected, cumulative), entry) =>
      if (selected.isDefined) {
        (selected, cumulative)
      } else {
        val next = cumulative + entry.p
        if (threshold <= next) {
          (Some(entry.msg), next)
        } else {
          (None, next)
        }
      }
    }._1.getOrElse(pdf.last.msg)
  }

  private def sendToOneNeighbor(
    neighbors: Map[Int, ActorRef],
    allowedOnEdge: Map[Int, Set[String]],
    kind: String,
    payload: String,
    metrics: ActorRef
  ): Unit = {
    val eligible = neighbors.keys.toVector.sorted.filter { to =>
      allowedOnEdge.getOrElse(to, Set.empty).contains(kind)
    }

    if (eligible.isEmpty) {
      log.warning("Node {} could not send {} because no outgoing edge allows it", id, kind)
      metrics ! MetricsActor.MessageDropped(id, kind, "no-eligible-edge")
    } else {
      val to = eligible(rng.nextInt(eligible.size))
      neighbors(to) ! Envelope(from = id, kind = kind, payload = payload)
      metrics ! MetricsActor.MessageSent(id, to, kind)
    }
  }
}
