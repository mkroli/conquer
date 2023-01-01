import com.typesafe.sbt.packager.MappingsHelper.directory

lazy val Benchmark = config("bench") extend Test

lazy val root = project
  .in(file("."))
  .configs(Benchmark)
  .settings(inConfig(Benchmark)(Defaults.testSettings): _*)
  .enablePlugins(BuildInfoPlugin, JavaAppPackaging)
  .settings(
    name := "conquer",
    version := "0.1.0",
    organization := "com.github.mkroli",
    organizationName := "Michael Krolikowski",
    startYear := Some(2023),
    licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
    scalaVersion := "2.13.10",
    resolvers += "confluent" at "https://packages.confluent.io/maven/",
    libraryDependencies ++= Seq(
      "ch.qos.logback"               % "logback-classic"                % "1.4.5",
      "org.typelevel"                %% "log4cats-slf4j"                % "2.5.0",
      "org.typelevel"                %% "cats-effect"                   % "3.4.3",
      "com.github.pureconfig"        %% "pureconfig"                    % "0.17.2",
      "com.github.pureconfig"        %% "pureconfig-ip4s"               % "0.17.2",
      "com.github.pureconfig"        %% "pureconfig-cats-effect"        % "0.17.2",
      "com.github.pureconfig"        %% "pureconfig-yaml"               % "0.17.2",
      "org.http4s"                   %% "http4s-ember-server"           % "0.23.16",
      "org.http4s"                   %% "http4s-dsl"                    % "0.23.16",
      "org.http4s"                   %% "http4s-circe"                  % "0.23.16",
      "com.softwaremill.sttp.tapir"  %% "tapir-http4s-server"           % "1.2.4",
      "com.softwaremill.sttp.tapir"  %% "tapir-json-circe"              % "1.2.4",
      "com.softwaremill.sttp.tapir"  %% "tapir-swagger-ui-bundle"       % "1.2.4",
      "io.circe"                     %% "circe-generic"                 % "0.14.3",
      "io.circe"                     %% "circe-parser"                  % "0.14.3",
      "com.github.fd4s"              %% "fs2-kafka"                     % "2.5.0",
      "com.github.fd4s"              %% "fs2-kafka-vulcan"              % "2.5.0",
      "com.github.fd4s"              %% "vulcan"                        % "1.8.4",
      "com.github.fd4s"              %% "vulcan-generic"                % "1.8.4",
      "io.micrometer"                % "micrometer-registry-prometheus" % "1.10.2",
      "org.scalatest"                %% "scalatest-freespec"            % "3.2.14" % Test,
      "org.scalatestplus"            %% "scalacheck-1-17"               % "3.2.14.0" % Test,
      "com.github.alexarchambault"   %% "scalacheck-shapeless_1.16"     % "1.3.1" % Test,
      "eu.timepit"                   %% "refined"                       % "0.10.1" % Test,
      "eu.timepit"                   %% "refined-scalacheck"            % "0.10.1" % Test,
      "org.typelevel"                %% "cats-effect-testing-scalatest" % "1.5.0" % Test,
      "com.storm-enroute"            %% "scalameter"                    % "0.21" % Benchmark,
      "com.fasterxml.jackson.module" %% "jackson-module-scala"          % "2.14.1" % Benchmark
    ),
    Compile / unmanagedResourceDirectories += (Compile / sourceDirectory).value / "conf",
    makeBatScripts := Nil,
    Universal / mappings ++= directory((Compile / sourceDirectory).value / "conf"),
    scriptClasspath := "../conf" +: scriptClasspath.value,
    Benchmark / testFrameworks += new TestFramework("org.scalameter.ScalaMeterFramework"),
    Benchmark / logBuffered := false,
    Benchmark / parallelExecution := false,
    Benchmark / fork := true,
    headerSettings(Benchmark)
  )
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version),
    buildInfoPackage := "com.github.mkroli.conquer"
  )
