package com.uic.cs553.distributed.runtime

final class EchoRuntimeAlgorithm(nodeId: Int, initiatorNode: Int) extends DistributedAlgorithm {
  private var currentWaveId = 0
  private var parent: Option[Int] = None
  private var children: Set[Int] = Set.empty
  private var repliesReceived: Set[Int] = Set.empty
  private var waveSeen = false

  override val name: String = "echo"

  override def onStart(ctx: NodeContext): Unit =
    if (nodeId == initiatorNode) {
      currentWaveId += 1
      waveSeen = true
      children = ctx.outgoingNeighbors.toSet
      repliesReceived = Set.empty
      ctx.logInfo(s"[echo] initiator starting wave $currentWaveId")
      ctx.emitAlgorithmEvent(name, "started")
      ctx.emitAlgorithmEvent(name, "visited")
      if (children.isEmpty) {
        ctx.logInfo(s"[echo] initiator $nodeId completed immediately")
        ctx.emitAlgorithmEvent(name, "completed")
      }
      else ctx.sendToAll("CONTROL", s"echo:wave:$currentWaveId:$initiatorNode")
    }

  override def onMessage(ctx: NodeContext, envelope: RuntimeEnvelope): Unit =
    if (envelope.kind == "CONTROL") {
      val parts = envelope.payload.split(":").toVector
      parts match {
        case Vector("echo", "wave", waveIdValue, originValue) =>
          onWave(ctx, envelope.from, waveIdValue.toInt, originValue.toInt)
        case Vector("echo", "reply", waveIdValue, originValue) =>
          onReply(ctx, envelope.from, waveIdValue.toInt, originValue.toInt)
        case _ =>
      }
    }

  private def onWave(ctx: NodeContext, from: Int, waveId: Int, origin: Int): Unit =
    if (!waveSeen || waveId > currentWaveId) {
      waveSeen = true
      currentWaveId = waveId
      parent = Some(from)
      children = ctx.outgoingNeighbors.filterNot(_ == from).toSet
      repliesReceived = Set.empty
      ctx.logInfo(s"[echo] node $nodeId accepted wave $waveId from $from")
      ctx.emitAlgorithmEvent(name, "visited")
      if (children.isEmpty) {
        ctx.send(from, "CONTROL", s"echo:reply:$waveId:$origin")
      } else {
        children.foreach(child => ctx.send(child, "CONTROL", s"echo:wave:$waveId:$origin"))
      }
    }

  private def onReply(ctx: NodeContext, from: Int, waveId: Int, origin: Int): Unit =
    if (waveSeen && waveId == currentWaveId) {
      repliesReceived = repliesReceived + from
      if (repliesReceived == children) {
        parent match {
          case Some(parentId) =>
            ctx.send(parentId, "CONTROL", s"echo:reply:$waveId:$origin")
            ctx.logInfo(s"[echo] node $nodeId replied to parent $parentId")
          case None if nodeId == initiatorNode =>
            ctx.logInfo(s"[echo] initiator $nodeId completed wave $waveId")
            ctx.emitAlgorithmEvent(name, "completed")
          case _ =>
        }
      }
    }
}
