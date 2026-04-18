package com.uic.cs553.distributed.runtime

object DistributedAlgorithmFactory {
  val supportedAlgorithms: Set[String] = Set("echo", "wave")

  def create(enabled: Vector[String], nodeId: Int, initiatorNode: Int): Vector[DistributedAlgorithm] =
    enabled.map(_.trim.toLowerCase).distinct.flatMap {
      case "echo" => Some(new EchoRuntimeAlgorithm(nodeId, initiatorNode))
      case "wave" => Some(new WaveRuntimeAlgorithm(nodeId, initiatorNode))
      case _      => None
    }
}
