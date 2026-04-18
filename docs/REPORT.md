# CS553 Project Report

## Overview

This project turns NetGameSim-generated graphs into Akka classic actor systems. NetGameSim is used only to generate and export graph artifacts. The simulator loads those artifacts, enriches the graph with message labels and node PDFs, creates one actor per graph node, and runs distributed algorithms on the resulting topology.

## Architecture

The main runtime path is:

```text
NetGameSim JSON export
  -> NetGameSimGraphLoader
  -> SimGraph
  -> SimulationConfig enrichment
  -> SimulationBootstrap
  -> NodeActor instances and ActorRef channels
  -> DistributedAlgorithm plug-ins
  -> MetricsActor summary
```

Each graph node becomes one Akka classic actor. Each graph edge becomes an outgoing neighbor reference stored by the source node actor. The runtime uses `Envelope(from, kind, payload)` messages for inter-node traffic.

## Graph Enrichment

NetGameSim exports the raw graph topology. The simulator enriches that topology using configuration:

- `sim.edgeLabeling.default` and `sim.edgeLabeling.overrides` define allowed message kinds per edge.
- `sim.traffic.defaultPdf` and `sim.traffic.perNodePdf` define node message distributions.
- `sim.initiators.timers` defines periodic timer nodes.
- `sim.initiators.inputs` defines nodes that accept external injections.

The enriched graph is serialized on each run when `--out <dir>` is provided:

- `enriched-graph.json` contains nodes, edges, labels, PDFs, initiators, and enabled algorithms.
- `enriched-graph.dot` visualizes the graph with edge labels.

## Initiation Modes

Timer initiation is implemented by `NodeActor` using Akka classic `Timers`. A timer node samples from its configured PDF or uses a fixed message kind.

Input initiation is implemented in two forms:

- File-driven injection using `--inject <file>`.
- Interactive injection using `--interactive`.

Interactive commands use this format:

```text
inject <nodeId> <kind> <payload>
quit
```

## Algorithms

The assigned runtime algorithms are Echo and Wave.

### Echo

The Echo implementation performs broadcast and convergcast using `CONTROL` messages:

- The initiator sends `echo:wave:<waveId>:<origin>` to outgoing neighbors.
- Each first-time receiver records its parent and forwards the wave to children.
- Leaf nodes reply with `echo:reply:<waveId>:<origin>`.
- The initiator completes when all expected replies return.

Assumption: Echo requires reverse `CONTROL` paths so replies can return toward parents. The simulator validates this assumption before startup.

### Wave

The Wave implementation is a flooding-style wave:

- The initiator sends `wave:probe:<waveId>:<origin>` to outgoing neighbors.
- Each node observes each wave id once.
- First-time observers forward the probe to outgoing neighbors except the sender.

Assumption: Wave can run on directed graphs, but it only reaches nodes reachable from the initiator along directed edges.

## Topology Validation

`TopologyValidator` checks enabled algorithms before actors start. It verifies:

- The configured initiator exists.
- Echo has reverse `CONTROL` paths for enabled forward `CONTROL` edges.
- Wave initiator has outgoing neighbors.

This prevents misleading runs where an algorithm appears to execute but violates its model assumptions.

## Experiments

### Directed NetGameSim Graph With Wave

Generate a real graph:

```bash
cd netgamesim
sbt "runMain com.lsc.Main realgraph"
cd ..
```

Run Wave on the directed NetGameSim graph:

```bash
sbt "runMain com.uic.cs553.distributed.cli.SimMain --graph netgamesim/outputs/realgraph.ngs --config conf/wave-directed.conf --out outputs/wave-run"
```

Observed result from a 26-node, 25-edge generated graph:

```text
sent=Map(CONTROL -> 25, PING -> 13, WORK -> 7)
received=Map(CONTROL -> 25, PING -> 13, WORK -> 7)
dropped=Map()
algorithmEvents=Map(wave.started -> 1, wave.observed -> 26)
```

### Bidirectional Echo+Wave Graph

Run Echo and Wave on the checked-in compatible topology:

```bash
sbt "runMain com.uic.cs553.distributed.cli.SimMain --graph examples/bidirectional-echo-graph.json --config conf/echo-wave-bidirectional.conf --out outputs/echo-wave-run"
```

Observed result:

```text
sent=Map(CONTROL -> 6, PING -> 13, WORK -> 7)
received=Map(CONTROL -> 6, PING -> 13, WORK -> 7)
dropped=Map()
algorithmEvents=HashMap(wave.observed -> 3, echo.visited -> 3, echo.started -> 1, wave.started -> 1, echo.completed -> 1)
```

## Cinnamon

Cinnamon / Akka Insights configuration is present in `application.conf`, and exact enablement instructions are in `docs/CINNAMON.md`. The plugin is disabled by default because the artifacts require Akka commercial credentials and a tokenized resolver. Keeping it disabled preserves a clean-machine `sbt test` path.

## Limitations

- Echo is intentionally rejected on directed graphs that lack reverse control paths.
- Wave reports reachability from the initiator, not global graph coverage unless the directed graph is reachable from that node.
- Cinnamon is documented but not enabled by default because credentials are required.
