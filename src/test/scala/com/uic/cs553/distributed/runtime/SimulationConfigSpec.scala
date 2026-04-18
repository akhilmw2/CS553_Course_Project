package com.uic.cs553.distributed.runtime

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SimulationConfigSpec extends AnyWordSpec with Matchers {
  "SimulationConfig" should {
    "load the simulator section from application config" in {
      val config = SimulationConfig.fromConfig(ConfigFactory.load()).toOption.get

      config.messageTypes should contain ("PING")
      config.enabledAlgorithms should contain ("echo")
      config.algorithmInitiatorNode shouldBe 0
      config.allowedMessagesFor(0, 1) should contain ("WORK")
      config.pdfForNode(0).map(_.msg) should contain ("WORK")
      config.timerFor(0).map(_.mode) shouldBe Some("pdf")
      config.isInputNode(0) shouldBe true
    }
  }
}
