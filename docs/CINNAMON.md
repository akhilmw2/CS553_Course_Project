# Cinnamon / Akka Insights

The simulator includes Cinnamon actor instrumentation configuration in
`src/main/resources/application.conf`, but the sbt plugin is disabled by default.

Reason: Akka Insights / Cinnamon artifacts are commercial Akka artifacts. The
official Akka Insights sbt setup states that `sbt-cinnamon` is public, but the
Cinnamon agent and instrumentation dependencies require Akka commercial
credentials and a tokenized resolver. Without those credentials, enabling the
plugin breaks `sbt test` on a clean machine.

To enable Cinnamon on a machine with Akka credentials:

1. Configure the Akka tokenized resolver using the credentials instructions from
   https://akka.io/token.
2. Uncomment this line in `project/plugins.sbt`:

   ```scala
   addSbtPlugin("com.lightbend.cinnamon" % "sbt-cinnamon" % "2.21.4")
   ```

3. Add the following Cinnamon settings to `build.sbt`:

   ```scala
   enablePlugins(Cinnamon)

   run / cinnamon := true
   test / cinnamon := true

   cinnamonLogLevel := "INFO"

   libraryDependencies += Cinnamon.library.cinnamonAkka
   libraryDependencies += Cinnamon.library.cinnamonCHMetrics
   libraryDependencies += Cinnamon.library.cinnamonJvmMetricsProducer
   ```

4. Run:

   ```bash
   sbt test
   sbt "runMain com.uic.cs553.distributed.cli.SimMain --graph netgamesim/outputs/realgraph.ngs --config conf/wave-directed.conf"
   ```

The active application configuration instruments user actors:

```hocon
cinnamon {
  akka.actors = {
    default-by-class {
      includes = "/user/*"
      report-by = class
    }
  }
}
```

This keeps the normal project build reproducible while documenting the exact
Cinnamon setup required when Akka commercial credentials are available.
