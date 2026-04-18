package com.uic.cs553.distributed.runtime

import akka.actor.{ActorRef, ActorSystem}
import com.uic.cs553.distributed.model.SimGraph

final case class SimulationHandle(
  system: ActorSystem,
  nodeRefs: Map[Int, ActorRef],
  metrics: ActorRef
)

object SimulationBootstrap {
  def run(graph: SimGraph, config: SimulationConfig, injections: Vector[InjectionEvent]): SimulationHandle = {
    val system = ActorSystem("GraphSimulation")
    val metrics = system.actorOf(MetricsActor.props(), "metrics")
    val nodeRefs = graph.nodes.map { node =>
      node.id -> system.actorOf(NodeActor.props(node.id, config.randomSeed + node.id), s"node-${node.id}")
    }.toMap

    graph.nodes.foreach { node =>
      val outgoing = graph.outNeighbors(node.id)
      val neighbors = outgoing.map(to => to -> nodeRefs(to)).toMap
      val allowedOnEdge = outgoing.map(to => to -> config.allowedMessagesFor(node.id, to)).toMap
      val algorithms = DistributedAlgorithmFactory.create(config.enabledAlgorithms, node.id, config.algorithmInitiatorNode)

      nodeRefs(node.id) ! NodeActor.Init(
        neighbors = neighbors,
        allowedOnEdge = allowedOnEdge,
        pdf = config.pdfForNode(node.id),
        timer = config.timerFor(node.id),
        acceptsExternalInput = config.isInputNode(node.id),
        metrics = metrics,
        algorithms = algorithms
      )
    }

    import system.dispatcher
    injections.filter(_.node >= 0).foreach { event =>
      system.scheduler.scheduleOnce(event.at) {
        nodeRefs.get(event.node) match {
          case Some(nodeRef) => nodeRef ! NodeActor.ExternalInput(event.kind, event.payload)
          case None          => system.log.warning("Injection target node {} does not exist", event.node)
        }
      }
    }

    system.scheduler.scheduleOnce(config.duration) {
      metrics ! MetricsActor.PrintSummary
      system.log.info("Simulation complete")
      system.terminate()
    }

    system.log.info("Simulation started with {} nodes and {} edges", graph.nodes.size, graph.edges.size)
    SimulationHandle(system, nodeRefs, metrics)
  }
}
