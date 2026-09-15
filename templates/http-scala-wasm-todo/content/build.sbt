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
      libraryDependencies += "org.typelevel" %%% "jawn-ast" % "1.7.0",
      scalaJSWitDirectory := baseDirectory.value / "wit",
      scalaJSWitWorld := Some("todo"),
      scalaJSWitPackage := Some("spintodo"),
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
