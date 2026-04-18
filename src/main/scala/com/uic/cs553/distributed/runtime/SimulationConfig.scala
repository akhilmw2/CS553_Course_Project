package com.uic.cs553.distributed.runtime

import com.typesafe.config.{Config, ConfigFactory}

import java.io.File

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

final case class PdfEntry(msg: String, p: Double)

final case class TimerInitiator(
  node: Int,
  tickEvery: FiniteDuration,
  mode: String,
  fixedMsg: Option[String]
)

final case class InputInitiator(node: Int)

final case class SimulationConfig(
  randomSeed: Long,
  duration: FiniteDuration,
  messageTypes: Vector[String],
  enabledAlgorithms: Vector[String],
  algorithmInitiatorNode: Int,
  defaultAllowedMessages: Set[String],
  edgeOverrides: Map[(Int, Int), Set[String]],
  defaultPdf: Vector[PdfEntry],
  perNodePdf: Map[Int, Vector[PdfEntry]],
  timerInitiators: Map[Int, TimerInitiator],
  inputInitiators: Set[Int]
) {
  def allowedMessagesFor(from: Int, to: Int): Set[String] =
    edgeOverrides.getOrElse((from, to), defaultAllowedMessages)

  def pdfForNode(nodeId: Int): Vector[PdfEntry] =
    perNodePdf.getOrElse(nodeId, defaultPdf)

  def timerFor(nodeId: Int): Option[TimerInitiator] =
    timerInitiators.get(nodeId)

  def isInputNode(nodeId: Int): Boolean =
    inputInitiators.contains(nodeId)
}

object SimulationConfig {
  def load(): Either[String, SimulationConfig] =
    fromConfig(ConfigFactory.load())

  def load(configPath: String): Either[String, SimulationConfig] = {
    val file = File(configPath)
    if (!file.exists()) Left(s"Simulator config file does not exist: $configPath")
    else fromConfig(ConfigFactory.parseFile(file).resolve())
  }

  def fromConfig(config: Config): Either[String, SimulationConfig] = {
    val sim = config.getConfig("sim")
    val messageTypes = sim.getConfig("messages").getStringList("types").asScala.toVector
    val enabledAlgorithms =
      if (sim.hasPath("algorithms.enabled")) sim.getStringList("algorithms.enabled").asScala.toVector
      else Vector.empty
    val algorithmInitiatorNode =
      if (sim.hasPath("algorithms.initiatorNode")) sim.getInt("algorithms.initiatorNode")
      else 0
    val defaultAllowedMessages = sim.getConfig("edgeLabeling").getStringList("default").asScala.toSet
    val edgeOverrides = sim.getConfig("edgeLabeling").getConfigList("overrides").asScala.toVector.map { overrideConfig =>
      (overrideConfig.getInt("from") -> overrideConfig.getInt("to")) ->
        overrideConfig.getStringList("allow").asScala.toSet
    }.toMap
    val defaultPdf = parsePdf(sim.getConfigList("traffic.defaultPdf").asScala.toVector)
    val perNodePdf = sim.getConfigList("traffic.perNodePdf").asScala.toVector.map { entry =>
      entry.getInt("node") -> parsePdf(entry.getConfigList("pdf").asScala.toVector)
    }.toMap
    val timerInitiators = sim.getConfig("initiators").getConfigList("timers").asScala.toVector.map { timer =>
      val mode = timer.getString("mode")
      timer.getInt("node") -> TimerInitiator(
        node = timer.getInt("node"),
        tickEvery = timer.getLong("tickEveryMs").millis,
        mode = mode,
        fixedMsg = if (timer.hasPath("fixedMsg")) Some(timer.getString("fixedMsg")) else None
      )
    }.toMap
    val inputInitiators = sim.getConfig("initiators").getConfigList("inputs").asScala.toVector.map(_.getInt("node")).toSet
    val cfg = SimulationConfig(
      randomSeed = sim.getLong("seed"),
      duration = sim.getLong("run.durationSeconds").seconds,
      messageTypes = messageTypes,
      enabledAlgorithms = enabledAlgorithms,
      algorithmInitiatorNode = algorithmInitiatorNode,
      defaultAllowedMessages = defaultAllowedMessages,
      edgeOverrides = edgeOverrides,
      defaultPdf = defaultPdf,
      perNodePdf = perNodePdf,
      timerInitiators = timerInitiators,
      inputInitiators = inputInitiators
    )

    validate(cfg)
  }

  private def parsePdf(entries: Vector[Config]): Vector[PdfEntry] =
    entries.map(entry => PdfEntry(entry.getString("msg"), entry.getDouble("p")))

  private def validate(config: SimulationConfig): Either[String, SimulationConfig] = {
    val allPdfs = config.defaultPdf +: config.perNodePdf.values.toVector
    val invalidPdf = allPdfs.find(pdf => math.abs(pdf.map(_.p).sum - 1.0d) > 1e-6)
    val invalidMessages =
      (config.defaultAllowedMessages ++ config.edgeOverrides.values.flatten ++ config.defaultPdf.map(_.msg) ++ config.perNodePdf.values.flatten.map(_.msg))
        .diff(config.messageTypes.toSet)

    if (config.defaultPdf.isEmpty) {
      Left("sim.traffic.defaultPdf must not be empty")
    } else if (invalidPdf.isDefined) {
      Left(s"Probability entries must sum to 1.0, found ${invalidPdf.get.map(_.p).sum}")
    } else if (invalidMessages.nonEmpty) {
      Left(s"Unknown message types in simulation config: ${invalidMessages.toVector.sorted.mkString(", ")}")
    } else if (config.enabledAlgorithms.exists(name => !DistributedAlgorithmFactory.supportedAlgorithms.contains(name))) {
      Left(
        s"Unsupported algorithms in simulation config: ${config.enabledAlgorithms.filterNot(DistributedAlgorithmFactory.supportedAlgorithms.contains).mkString(", ")}"
      )
    } else {
      Right(config)
    }
  }
}
