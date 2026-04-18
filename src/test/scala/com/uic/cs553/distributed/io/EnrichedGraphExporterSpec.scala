package com.uic.cs553.distributed.io

import com.typesafe.config.ConfigFactory
import com.uic.cs553.distributed.model.{SimEdge, SimGraph, SimNode}
import com.uic.cs553.distributed.runtime.SimulationConfig
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Files

class EnrichedGraphExporterSpec extends AnyWordSpec with Matchers {
  "EnrichedGraphExporter" should {
    "write JSON and DOT artifacts with edge labels and node PDFs" in {
      val config = SimulationConfig.fromConfig(ConfigFactory.load()).toOption.get
      val graph = SimGraph(
        initialNodeId = 0,
        nodes = Vector(SimNode(0, 1.0d, false), SimNode(1, 2.0d, true)),
        edges = Vector(SimEdge(0, 1, 10, 0.5d))
      )
      val outDir = Files.createTempDirectory("enriched-graph")

      EnrichedGraphExporter.write(graph, config, outDir) shouldBe Right(())

      val json = Files.readString(outDir.resolve("enriched-graph.json"))
      val dot = Files.readString(outDir.resolve("enriched-graph.dot"))

      json should include("allowedMessages")
      json should include("pdf")
      dot should include("0 -> 1")
      dot should include("CONTROL")
    }
  }
}
