import org.scalajs.linker.interface.ESVersion
import org.scalajs.linker.interface.ModuleKind

ThisBuild / scalaVersion := "2.13.18"
ThisBuild / organization := "io.github.scala-wasm.examples"

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
        scalaJSLinkerConfig.value
          .withPrettyPrint(true)
          .withESFeatures { features =>
            features
              .withUseWebAssembly(true)
              .withESVersion(ESVersion.ES2022)
          }
          .withModuleKind(ModuleKind.WasmComponent)
      },
  )
