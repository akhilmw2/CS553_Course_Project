package com.uic.cs553.distributed.runtime

final class WaveRuntimeAlgorithm(nodeId: Int, initiatorNode: Int) extends DistributedAlgorithm {
  private var currentWaveId = 0
  private var seenWaveIds: Set[Int] = Set.empty

  override val name: String = "wave"

  override def onStart(ctx: NodeContext): Unit =
    if (nodeId == initiatorNode) {
      currentWaveId += 1
      seenWaveIds = seenWaveIds + currentWaveId
      ctx.logInfo(s"[wave] initiator starting broadcast wave $currentWaveId")
      ctx.emitAlgorithmEvent(name, "started")
      ctx.emitAlgorithmEvent(name, "observed")
      ctx.sendToAll("CONTROL", s"wave:probe:$currentWaveId:$initiatorNode")
    }

  override def onMessage(ctx: NodeContext, envelope: RuntimeEnvelope): Unit =
    if (envelope.kind == "CONTROL") {
      val parts = envelope.payload.split(":").toVector
      parts match {
        case Vector("wave", "probe", waveIdValue, originValue) =>
          val waveId = waveIdValue.toInt
          if (!seenWaveIds.contains(waveId)) {
            seenWaveIds = seenWaveIds + waveId
            ctx.logInfo(s"[wave] node $nodeId observed wave $waveId from ${envelope.from}")
            ctx.emitAlgorithmEvent(name, "observed")
            ctx.sendToAll("CONTROL", s"wave:probe:$waveId:$originValue", except = Set(envelope.from))
          }
        case _ =>
      }
    }
}
