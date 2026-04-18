# Quick Start

This guide covers the graph-driven simulator path that uses NetGameSim exports and runs the assigned Echo and Wave algorithms.

## Prerequisites

1. **Install Java 11+**
   ```bash
   java -version
   ```

2. **Install SBT (Scala Build Tool)**
   
   On Ubuntu/Debian:
   ```bash
   echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | sudo tee /etc/apt/sources.list.d/sbt.list
   curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo apt-key add
   sudo apt-get update
   sudo apt-get install sbt
   ```
   
   On macOS:
   ```bash
   brew install sbt
   ```
   
   On Windows:
   Download from https://www.scala-sbt.org/download.html

## Building the Project

1. Clone the repository:
   ```bash
   git clone https://github.com/0x1DOCD00D/CS553_2026.git
   cd CS553_2026
   ```

2. Compile the project:
   ```bash
   sbt compile
   ```

## Running the Graph-Driven Simulator

1. Make sure the NetGameSim submodule is present:
   ```bash
   git submodule update --init --recursive
   ```

2. In NetGameSim, configure JSON output:
   ```hocon
   NGSimulator.OutputGraphRepresentation.contentType = "json"
   ```

3. Generate a graph export with NetGameSim.

4. Run the simulator against that export:
   ```bash
   sbt "runMain com.uic.cs553.distributed.cli.SimMain --graph /path/to/NetGameSimGraph.json --config conf/wave-directed.conf --out outputs/wave-run"
   ```

5. Optional: inject external messages from a file:
   ```bash
   sbt "runMain com.uic.cs553.distributed.cli.SimMain --graph /path/to/NetGameSimGraph.json --config conf/wave-directed.conf --inject /path/to/injections.txt"
   ```

6. Run Echo+Wave on a compatible bidirectional graph:
   ```bash
   sbt "runMain com.uic.cs553.distributed.cli.SimMain --graph examples/bidirectional-echo-graph.json --config conf/echo-wave-bidirectional.conf --out outputs/echo-wave-run"
   ```

7. Run interactive injection mode:
   ```bash
   sbt "runMain com.uic.cs553.distributed.cli.SimMain --graph examples/bidirectional-echo-graph.json --config conf/interactive-echo-wave.conf --interactive"
   ```

Interactive commands:

```text
inject 0 WORK bootstrap-job
inject 1 PING hello-world
quit
```

Injection file format:

```text
# delayMs nodeId kind payload
0 0 WORK bootstrap-job
1000 0 PING hello-world
```

## Running Tests

```bash
sbt test
```

## Development Workflow

### 1. Understanding the Runtime

Start by reading these files in order:
1. `runtime/SimulationBootstrap.scala` - Creates one Akka classic actor per graph node
2. `runtime/NodeActor.scala` - Enforces edge labels, timers, input injection, and algorithm callbacks
3. `runtime/DistributedAlgorithm.scala` - Plug-in interface for algorithms
4. `runtime/EchoRuntimeAlgorithm.scala` - Assigned Echo algorithm
5. `runtime/WaveRuntimeAlgorithm.scala` - Assigned Wave algorithm

### 2. Adding A Runtime Algorithm

Create a new file in `src/main/scala/com/uic/cs553/distributed/runtime/`:

```scala
package com.uic.cs553.distributed.runtime

final class MyRuntimeAlgorithm(nodeId: Int, initiatorNode: Int) extends DistributedAlgorithm {
  override val name: String = "my-runtime-algorithm"

  override def onStart(ctx: NodeContext): Unit =
    if (nodeId == initiatorNode) {
      ctx.sendToAll("CONTROL", "my-runtime-algorithm:start")
      ctx.emitAlgorithmEvent(name, "started")
    }
}
```

Then register it in `DistributedAlgorithmFactory.scala` and enable it in config.

### 3. Run The Simulator

```bash
sbt "runMain com.uic.cs553.distributed.cli.SimMain --graph examples/bidirectional-echo-graph.json --config conf/echo-wave-bidirectional.conf --out outputs/echo-wave-run"
```

## Debugging Tips

1. **Increase logging verbosity**: Edit `src/main/resources/logback.xml`
   ```xml
   <logger name="com.uic.cs553" level="DEBUG" />
   ```

2. **Add custom logging**:
   ```scala
   ctx.log.info(s"[$nodeId] Your debug message here")
   ```

3. **Interactive mode**: Use `sbt` shell for faster iteration
   ```bash
   sbt
   > compile
   > test
   > runMain com.uic.cs553.distributed.cli.SimMain --graph examples/bidirectional-echo-graph.json --config conf/interactive-echo-wave.conf --interactive
   ```

## Common Issues

### "Out of memory" errors
Increase JVM heap size:
```bash
export SBT_OPTS="-Xmx2G -Xss2M"
sbt compile
```

### Actor not receiving messages
Check these first:
1. The graph contains the expected node and edge.
2. The edge label allows the message kind.
3. The node is listed under `sim.initiators.inputs` for external injections.
4. The algorithm is enabled under `sim.algorithms.enabled`.

## Resources

- [Akka Documentation](https://doc.akka.io/docs/akka/current/)
- [Distributed Algorithms by Nancy Lynch](https://mitpress.mit.edu/9780262011549/)
- Course materials and lectures
