package com.uic.cs553.distributed.runtime

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class NodeActorSpec
    extends TestKit(ActorSystem("NodeActorSpec"))
    with ImplicitSender
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)

  "NodeActor" should {
    "deliver external input across an allowed edge" in {
      val neighbor = TestProbe()
      val metrics = TestProbe()
      val node = system.actorOf(NodeActor.props(id = 1, seed = 1L))

      node ! NodeActor.Init(
        neighbors = Map(2 -> neighbor.ref),
        allowedOnEdge = Map(2 -> Set("WORK")),
        pdf = Vector(PdfEntry("WORK", 1.0d)),
        timer = None,
        acceptsExternalInput = true,
        metrics = metrics.ref,
        algorithms = Vector.empty
      )

      node ! NodeActor.ExternalInput("WORK", "payload")

      neighbor.expectMsg(NodeActor.Envelope(1, "WORK", "payload"))
      metrics.expectMsgType[MetricsActor.MessageSent]
    }

    "drop external input when no edge permits that message kind" in {
      val neighbor = TestProbe()
      val metrics = TestProbe()
      val node = system.actorOf(NodeActor.props(id = 1, seed = 1L))

      node ! NodeActor.Init(
        neighbors = Map(2 -> neighbor.ref),
        allowedOnEdge = Map(2 -> Set("PING")),
        pdf = Vector(PdfEntry("PING", 1.0d)),
        timer = None,
        acceptsExternalInput = true,
        metrics = metrics.ref,
        algorithms = Vector.empty
      )

      node ! NodeActor.ExternalInput("WORK", "payload")

      neighbor.expectNoMessage()
      metrics.expectMsg(MetricsActor.MessageDropped(1, "WORK", "no-eligible-edge"))
    }
  }
}
