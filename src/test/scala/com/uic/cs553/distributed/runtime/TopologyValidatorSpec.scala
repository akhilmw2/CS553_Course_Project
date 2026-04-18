package com.uic.cs553.distributed.runtime

import com.typesafe.config.ConfigFactory
import com.uic.cs553.distributed.model.{SimEdge, SimGraph, SimNode}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TopologyValidatorSpec extends AnyWordSpec with Matchers {
  "TopologyValidator" should {
    "reject echo on a one-way topology" in {
      val config = SimulationConfig.fromConfig(ConfigFactory.load()).toOption.get
      val graph = SimGraph(
        initialNodeId = 0,
        nodes = Vector(SimNode(0, 0.0d, false), SimNode(1, 0.0d, false)),
        edges = Vector(SimEdge(0, 1, 1, 1.0d))
      )

      val validation = TopologyValidator.validate(graph, config)

      validation.errors.exists(_.contains("Echo requires a reverse CONTROL path")) shouldBe true
    }

    "accept echo on a bidirectional topology" in {
      val config = SimulationConfig.fromConfig(ConfigFactory.load()).toOption.get
      val graph = SimGraph(
        initialNodeId = 0,
        nodes = Vector(SimNode(0, 0.0d, false), SimNode(1, 0.0d, false)),
        edges = Vector(
          SimEdge(0, 1, 1, 1.0d),
          SimEdge(1, 0, 1, 1.0d)
        )
      )

      val validation = TopologyValidator.validate(graph, config)

      validation.errors shouldBe empty
    }
  }
}
