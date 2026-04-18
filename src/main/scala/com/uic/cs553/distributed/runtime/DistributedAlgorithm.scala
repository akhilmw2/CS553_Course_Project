package com.uic.cs553.distributed.runtime

import akka.actor.ActorRef

final case class RuntimeEnvelope(from: Int, kind: String, payload: String)

trait NodeContext {
  def nodeId: Int
  def outgoingNeighbors: Vector[Int]
  def send(to: Int, kind: String, payload: String): Unit
  def sendToAll(kind: String, payload: String, except: Set[Int] = Set.empty): Unit
  def emitAlgorithmEvent(algorithm: String, event: String): Unit
  def logInfo(message: String): Unit
  def logWarning(message: String): Unit
}

trait DistributedAlgorithm {
  def name: String
  def onStart(ctx: NodeContext): Unit = ()
  def onMessage(ctx: NodeContext, envelope: RuntimeEnvelope): Unit = ()
  def onTick(ctx: NodeContext): Unit = ()
}
