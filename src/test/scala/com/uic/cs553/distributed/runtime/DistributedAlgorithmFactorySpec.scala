package com.uic.cs553.distributed.runtime

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DistributedAlgorithmFactorySpec extends AnyWordSpec with Matchers {
  "DistributedAlgorithmFactory" should {
    "instantiate supported runtime algorithms" in {
      val algorithms = DistributedAlgorithmFactory.create(Vector("echo", "wave"), nodeId = 0, initiatorNode = 0)

      algorithms.map(_.name) shouldBe Vector("echo", "wave")
    }

    "ignore unknown runtime algorithms" in {
      val algorithms = DistributedAlgorithmFactory.create(Vector("echo", "unknown"), nodeId = 0, initiatorNode = 0)

      algorithms.map(_.name) shouldBe Vector("echo")
    }
  }
}
