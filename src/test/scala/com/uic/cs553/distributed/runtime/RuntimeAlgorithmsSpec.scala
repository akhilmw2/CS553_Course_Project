package com.uic.cs553.distributed.runtime

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class RuntimeAlgorithmsSpec extends AnyWordSpec with Matchers {
  "EchoRuntimeAlgorithm" should {
    "complete immediately when the initiator has no outgoing neighbors" in {
      val ctx = new RecordingNodeContext(0, Vector.empty)
      val initiator = new EchoRuntimeAlgorithm(nodeId = 0, initiatorNode = 0)

      initiator.onStart(ctx)

      ctx.sent shouldBe empty
      ctx.events should contain allOf ("echo.started", "echo.visited", "echo.completed")
      ctx.infos.exists(_.contains("completed immediately")) shouldBe true
    }

    "do nothing on start for non-initiator nodes" in {
      val ctx = new RecordingNodeContext(1, Vector(0, 2))
      val nonInitiator = new EchoRuntimeAlgorithm(nodeId = 1, initiatorNode = 0)

      nonInitiator.onStart(ctx)

      ctx.sent shouldBe empty
      ctx.events shouldBe empty
      ctx.infos shouldBe empty
    }

    "complete a wave on a bidirectional two-node topology" in {
      val initiatorCtx = new RecordingNodeContext(0, Vector(1))
      val childCtx = new RecordingNodeContext(1, Vector(0))
      val initiator = new EchoRuntimeAlgorithm(nodeId = 0, initiatorNode = 0)
      val child = new EchoRuntimeAlgorithm(nodeId = 1, initiatorNode = 0)

      initiator.onStart(initiatorCtx)

      initiatorCtx.sent should contain only ((1, "CONTROL", "echo:wave:1:0"))

      child.onMessage(childCtx, RuntimeEnvelope(0, "CONTROL", "echo:wave:1:0"))

      childCtx.sent should contain only ((0, "CONTROL", "echo:reply:1:0"))

      initiator.onMessage(initiatorCtx, RuntimeEnvelope(1, "CONTROL", "echo:reply:1:0"))

      initiatorCtx.infos.exists(_.contains("completed wave 1")) shouldBe true
      initiatorCtx.events should contain ("echo.completed")
    }

    "wait for all children before replying to its parent" in {
      val relayCtx = new RecordingNodeContext(1, Vector(0, 2, 3))
      val relay = new EchoRuntimeAlgorithm(nodeId = 1, initiatorNode = 0)

      relay.onMessage(relayCtx, RuntimeEnvelope(0, "CONTROL", "echo:wave:1:0"))

      relayCtx.sent should contain theSameElementsAs Vector(
        (2, "CONTROL", "echo:wave:1:0"),
        (3, "CONTROL", "echo:wave:1:0")
      )

      relay.onMessage(relayCtx, RuntimeEnvelope(2, "CONTROL", "echo:reply:1:0"))
      relayCtx.sent.exists(_ == (0, "CONTROL", "echo:reply:1:0")) shouldBe false

      relay.onMessage(relayCtx, RuntimeEnvelope(3, "CONTROL", "echo:reply:1:0"))
      relayCtx.sent should contain ((0, "CONTROL", "echo:reply:1:0"))
    }

    "ignore duplicate wave messages after choosing a parent" in {
      val ctx = new RecordingNodeContext(1, Vector(0))
      val node = new EchoRuntimeAlgorithm(nodeId = 1, initiatorNode = 0)

      node.onMessage(ctx, RuntimeEnvelope(0, "CONTROL", "echo:wave:1:0"))
      node.onMessage(ctx, RuntimeEnvelope(0, "CONTROL", "echo:wave:1:0"))

      ctx.sent shouldBe Vector((0, "CONTROL", "echo:reply:1:0"))
      ctx.events shouldBe Vector("echo.visited")
    }

    "ignore non-control envelopes" in {
      val ctx = new RecordingNodeContext(1, Vector(0))
      val node = new EchoRuntimeAlgorithm(nodeId = 1, initiatorNode = 0)

      node.onMessage(ctx, RuntimeEnvelope(0, "WORK", "echo:wave:1:0"))

      ctx.sent shouldBe empty
      ctx.events shouldBe empty
    }
  }

  "WaveRuntimeAlgorithm" should {
    "do nothing on start for non-initiator nodes" in {
      val ctx = new RecordingNodeContext(1, Vector(0, 2))
      val node = new WaveRuntimeAlgorithm(nodeId = 1, initiatorNode = 0)

      node.onStart(ctx)

      ctx.sent shouldBe empty
      ctx.events shouldBe empty
      ctx.infos shouldBe empty
    }

    "broadcast a wave once per node" in {
      val initiatorCtx = new RecordingNodeContext(0, Vector(1, 2))
      val relayCtx = new RecordingNodeContext(1, Vector(0, 2))
      val initiator = new WaveRuntimeAlgorithm(nodeId = 0, initiatorNode = 0)
      val relay = new WaveRuntimeAlgorithm(nodeId = 1, initiatorNode = 0)

      initiator.onStart(initiatorCtx)

      initiatorCtx.sent shouldBe Vector(
        (1, "CONTROL", "wave:probe:1:0"),
        (2, "CONTROL", "wave:probe:1:0")
      )

      relay.onMessage(relayCtx, RuntimeEnvelope(0, "CONTROL", "wave:probe:1:0"))

      relayCtx.sent should contain only ((2, "CONTROL", "wave:probe:1:0"))

      relay.onMessage(relayCtx, RuntimeEnvelope(0, "CONTROL", "wave:probe:1:0"))

      relayCtx.sent should have size 1
    }

    "record initiator start and observation events" in {
      val ctx = new RecordingNodeContext(0, Vector(1))
      val initiator = new WaveRuntimeAlgorithm(nodeId = 0, initiatorNode = 0)

      initiator.onStart(ctx)

      ctx.events shouldBe Vector("wave.started", "wave.observed")
      ctx.sent shouldBe Vector((1, "CONTROL", "wave:probe:1:0"))
    }

    "ignore duplicate probes and non-control envelopes" in {
      val ctx = new RecordingNodeContext(1, Vector(0, 2))
      val node = new WaveRuntimeAlgorithm(nodeId = 1, initiatorNode = 0)

      node.onMessage(ctx, RuntimeEnvelope(0, "WORK", "wave:probe:1:0"))
      node.onMessage(ctx, RuntimeEnvelope(0, "CONTROL", "wave:probe:1:0"))
      node.onMessage(ctx, RuntimeEnvelope(0, "CONTROL", "wave:probe:1:0"))

      ctx.sent shouldBe Vector((2, "CONTROL", "wave:probe:1:0"))
      ctx.events shouldBe Vector("wave.observed")
    }
  }
}

private final class RecordingNodeContext(
  override val nodeId: Int,
  override val outgoingNeighbors: Vector[Int]
) extends NodeContext {
  var sent: Vector[(Int, String, String)] = Vector.empty
  var events: Vector[String] = Vector.empty
  var infos: Vector[String] = Vector.empty
  var warnings: Vector[String] = Vector.empty

  override def send(to: Int, kind: String, payload: String): Unit =
    sent = sent :+ ((to, kind, payload))

  override def sendToAll(kind: String, payload: String, except: Set[Int]): Unit =
    outgoingNeighbors.filterNot(except.contains).foreach(send(_, kind, payload))

  override def emitAlgorithmEvent(algorithm: String, event: String): Unit =
    events = events :+ s"$algorithm.$event"

  override def logInfo(message: String): Unit =
    infos = infos :+ message

  override def logWarning(message: String): Unit =
    warnings = warnings :+ message
}
