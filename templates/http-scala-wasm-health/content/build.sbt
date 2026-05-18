import org.scalajs.linker.interface.ModuleKind

ThisBuild / scalaVersion := "2.13.18"
ThisBuild / organization := "io.github.scala-wasm.examples"

resolvers += "Sonatype Central Snapshots" at "https://central.sonatype.com/repository/maven-snapshots/"

lazy val root = project.in(file("."))
  .enablePlugins(ScalaJSPlugin)
  .settings(
      name := "{{project-name | kebab_case}}",
      version := "0.1.0",
      moduleName := "{{project-name | kebab_case}}",
      scalaJSWitDirectory := baseDirectory.value / "wit",
      scalaJSWitWorld := Some("health"),
      scalaJSWitPackage := Some("spinhealth"),
      scalaJSLinkerConfig := {
        val witDir = scalaJSWitDirectory.value
        val witWorld = scalaJSWitWorld.value
        scalaJSLinkerConfig.value
          .withPrettyPrint(true)
          .withExperimentalUseWebAssembly(true)
          .withModuleKind(ModuleKind.WasmComponent)
          .withWasmFeatures { features =>
            features
              .withWitDirectory(Some(witDir.getAbsolutePath))
              .withWitWorld(witWorld)
          }
      },
  )
