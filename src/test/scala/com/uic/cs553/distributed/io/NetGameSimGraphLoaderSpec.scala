package com.uic.cs553.distributed.io

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import java.nio.file.Files

class NetGameSimGraphLoaderSpec extends AnyWordSpec with Matchers {

  "NetGameSimGraphLoader" should {
    "load a NetGameSim JSON export into the internal graph model" in {
      val graphFile = Files.createTempFile("netgamesim-graph", ".json")
      Files.writeString(
        graphFile,
        """[{"id":0,"storedValue":0.5,"valuableData":false},{"id":1,"storedValue":1.5,"valuableData":true}]
          |[{"actionType":3,"fromNode":{"id":0,"storedValue":0.5,"valuableData":false},"toNode":{"id":1,"storedValue":1.5,"valuableData":true},"cost":2.5}]""".stripMargin,
        StandardCharsets.UTF_8
      )

      val graph = NetGameSimGraphLoader.load(graphFile).toOption.get

      graph.initialNodeId shouldBe 0
      graph.nodes.map(_.id) shouldBe Vector(0, 1)
      graph.outNeighbors(0) shouldBe Vector(1)
      graph.inNeighbors(1) shouldBe Vector(0)
      graph.edge(0, 1).map(_.actionType) shouldBe Some(3)
    }

    "reject exports with missing edge endpoints" in {
      val graphFile = Files.createTempFile("netgamesim-invalid", ".json")
      Files.writeString(
        graphFile,
        """[{"id":4,"storedValue":0.5,"valuableData":false}]
          |[{"actionType":3,"fromNode":{"id":4,"storedValue":0.5,"valuableData":false},"toNode":{"id":9,"storedValue":1.5,"valuableData":true},"cost":2.5}]""".stripMargin,
        StandardCharsets.UTF_8
      )

      val result = NetGameSimGraphLoader.load(graphFile)

      result.left.toOption.get should include("missing nodes")
    }
  }
}
