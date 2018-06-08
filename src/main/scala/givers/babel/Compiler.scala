package givers.babel

import java.io.File
import java.nio.file.{Files, Path}

import sbt.internal.util.ManagedLogger

case class CompilationEntry(success: Boolean, inputFile: File, filesRead: Set[Path], filesWritten: Set[Path])
case class Input(name: String, path: Path)

class Shell {
  def execute(cmd: String, cwd: File, envs: (String, String)*): Int = {
    import scala.sys.process._

    Process(cmd, cwd, envs:_*).!
  }
}

class Compiler(
  binary: File,
  babelRc: File,
  sourceDir: File,
  targetDir: File,
  logger: ManagedLogger,
  nodeModules: File,
  shell: Shell = new Shell
) {

  def compile(inputFiles: Seq[Path]): Seq[CompilationEntry] = {
    import sbt._

    if (inputFiles.isEmpty) {
      return Seq.empty
    }

    val inputs = inputFiles.map { inputFile =>
      val name = sourceDir.toPath.relativize((inputFile.getParent.toFile / inputFile.toFile.getName).toPath).toString
      Input(name, inputFile)
    }

    inputs.map { input =>
      val outputFile = targetDir / input.name
      Files.createDirectories(outputFile.getParentFile.toPath)
      val cmd = s"$binary ${input.path.toFile.getAbsolutePath} --out-file ${outputFile.getAbsolutePath} --config-file ${babelRc.getAbsolutePath}"
      logger.info(s"[Babel] $cmd")

      val exitCode = shell.execute(cmd, new File("."), "NODE_PATH" -> nodeModules.getCanonicalPath)

      CompilationEntry(
        success = exitCode == 0,
        inputFile = input.path.toFile,
        filesRead = Set(input.path),
        filesWritten = Set(outputFile.toPath)
      )
    }
  }
}
