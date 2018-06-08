package givers.babel

import com.typesafe.sbt.web._
import com.typesafe.sbt.web.incremental._
import sbt.Keys._
import sbt._
import xsbti.{Position, Problem, Severity}

object SbtBabel extends AutoPlugin {

  override def requires = SbtWeb

  override def trigger = AllRequirements

  object autoImport {
    object BabelKeys {
      val babel = TaskKey[Seq[File]]("babel", "Run babel")
      val binary = SettingKey[String]("babelBinary", "The location of babel binary")
      val babelRc = SettingKey[String]("babelRc", "The location of .babelrc")
      val nodeModulesPath = TaskKey[String]("babelNodeModules", "The location of the node_modules.")
    }
  }

  import SbtWeb.autoImport._
  import WebKeys._
  import autoImport._
  import BabelKeys._

  override def projectSettings: Seq[Setting[_]] = inConfig(Assets)(Seq(
    binary := "please-define-the-binary",
    babelRc := "please-define-the-location-of-babelrc",
    excludeFilter in babel := HiddenFileFilter || "_*",
    includeFilter in babel := "*.js",
    nodeModulesPath := "./node_modules",
    resourceManaged in babel := webTarget.value / "babel" / "main",
    managedResourceDirectories in Assets+= (resourceManaged in babel in Assets).value,
    resourceGenerators in Assets += babel in Assets,
    // Because sbt-babel compiles JS and output into the same file. Therefore, we need to deduplicate the files.
    // Otherwise, the "duplicate mappings" error would occur.
    deduplicators in Assets += {
      val targetDir = (resourceManaged in babel in Assets).value
      val targetDirAbsolutePath = targetDir.getAbsolutePath

      { files: Seq[File] => files.find(_.getAbsolutePath.startsWith(targetDirAbsolutePath)) }
    },
    babel in Assets := task.dependsOn(WebKeys.webModules in Assets).value
  ))


  lazy val task = Def.task {
    val sourceDir = (sourceDirectory in Assets).value
    val targetDir = (resourceManaged in babel in Assets).value
    val logger = (streams in Assets).value.log
    val nodeModulesLocation = (nodeModulesPath in babel).value
    val babelReporter = (reporter in Assets).value
    val babelBinaryLocation = (binary in babel).value
    val babelRcLocation = (babelRc in babel).value

    val sources = (sourceDir ** ((includeFilter in babel in Assets).value -- (excludeFilter in babel in Assets).value)).get

    implicit val fileHasherIncludingOptions = OpInputHasher[File] { f =>
      OpInputHash.hashString(Seq(
        "babel",
        f.getCanonicalPath,
        sourceDir.getAbsolutePath
      ).mkString("--"))
    }

    val results = incremental.syncIncremental((streams in Assets).value.cacheDirectory / "run", sources) { modifiedSources =>
      val startInstant = System.currentTimeMillis

      if (modifiedSources.nonEmpty) {
        logger.info(s"[Babel] Compile on ${modifiedSources.size} changed files")
      } else {
        logger.info(s"[Babel] No changes to compile")
      }

      val compiler = new Compiler(
        new File(babelBinaryLocation),
        new File(babelRcLocation),
        sourceDir,
        targetDir,
        logger,
        new File(nodeModulesLocation))

      // Compile all modified sources at once
      val entries = compiler.compile(modifiedSources.map(_.toPath))

      // Report compilation problems
      CompileProblems.report(
        reporter = babelReporter,
        problems = if (!entries.forall(_.success)) {
          Seq(new Problem {
            override def category() = ""

            override def severity() = Severity.Error

            override def message() = ""

            override def position() = new Position {
              override def line() = java.util.Optional.empty()

              override def lineContent() = ""

              override def offset() = java.util.Optional.empty()

              override def pointer() = java.util.Optional.empty()

              override def pointerSpace() = java.util.Optional.empty()

              override def sourcePath() = java.util.Optional.empty()

              override def sourceFile() = java.util.Optional.empty()
            }
          })
        } else { Seq.empty }
      )

      // Collect OpResults
      val opResults: Map[File, OpResult] = entries
        .map { entry =>
          entry.inputFile -> OpSuccess(entry.filesRead.map(_.toFile), entry.filesWritten.map(_.toFile))
        }
        .toMap

      // Collect the created files
      val createdFiles = entries.flatMap(_.filesWritten.map(_.toFile))

      val endInstant = System.currentTimeMillis

      if (createdFiles.nonEmpty) {
        logger.info(s"[Babel] finished compilation in ${endInstant - startInstant} ms and generated ${createdFiles.size} JS files")
      }

      (opResults, createdFiles)

    }(fileHasherIncludingOptions)

    // Return the dependencies
    (results._1 ++ results._2.toSet).toSeq
  }
}
