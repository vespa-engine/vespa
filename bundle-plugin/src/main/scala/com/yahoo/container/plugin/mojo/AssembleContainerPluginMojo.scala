// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.mojo

import java.io.File
import java.nio.channels.Channels
import java.util.jar.JarFile
import java.util.zip.ZipEntry

import com.yahoo.container.plugin.util.{Files, JarFiles}
import org.apache.maven.archiver.{MavenArchiveConfiguration, MavenArchiver}
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.{Mojo, Parameter, ResolutionScope}
import org.apache.maven.project.MavenProject
import org.codehaus.plexus.archiver.jar.JarArchiver

import scala.collection.convert.wrapAsScala._

/**
 * @author  tonytv
 */
@Mojo(name = "assemble-container-plugin", requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
class AssembleContainerPluginMojo extends AbstractMojo {
  object withDependencies
  object withoutDependencies

  @Parameter(defaultValue = "${project}")
  var project: MavenProject = null

  @Parameter(defaultValue = "${session}", readonly = true, required = true)
  var session: MavenSession = null

  @Parameter
  var archiveConfiguration: MavenArchiveConfiguration = new MavenArchiveConfiguration

  @Parameter(alias = "UseCommonAssemblyIds", defaultValue = "false")
  var useCommonAssemblyIds: Boolean = false


   def execute() {
    val jarSuffixes =
      if (useCommonAssemblyIds) Map(withoutDependencies -> ".jar", withDependencies -> "-jar-with-dependencies.jar")
      else Map(withoutDependencies -> "-without-dependencies.jar", withDependencies -> "-deploy.jar")

    val jarFiles = jarSuffixes mapValues jarFileInBuildDirectory(build.getFinalName)

    //force recreating the archive
    archiveConfiguration.setForced(true)
    archiveConfiguration.setManifestFile(new File(new File(project.getBuild.getOutputDirectory), JarFile.MANIFEST_NAME))

    val jarWithoutDependencies = new JarArchiver()
    addClassesDirectory(jarWithoutDependencies)
    createArchive(jarFiles(withoutDependencies), jarWithoutDependencies)
    project.getArtifact.setFile(jarFiles(withoutDependencies))

    val jarWithDependencies = new JarArchiver()
    addClassesDirectory(jarWithDependencies)
    addDependencies(jarWithDependencies)
    createArchive(jarFiles(withDependencies), jarWithDependencies)
  }

  private def jarFileInBuildDirectory(name: String)(jarSuffix: String) = {
    new File(build.getDirectory, name + jarSuffix)
  }

  private def addClassesDirectory(jarArchiver: JarArchiver) {
    val classesDirectory = new File(build.getOutputDirectory)
    if (classesDirectory.isDirectory) {
      jarArchiver.addDirectory(classesDirectory)
    }
  }

  private def createArchive(jarFile: File, jarArchiver: JarArchiver) {
    val mavenArchiver = new MavenArchiver
    mavenArchiver.setArchiver(jarArchiver)
    mavenArchiver.setOutputFile(jarFile)
    mavenArchiver.createArchive(session, project, archiveConfiguration)
  }

  private def addDependencies(jarArchiver: JarArchiver) {
    Artifacts.getArtifactsToInclude(project).foreach { artifact =>
        if (artifact.getType == "jar") {
          jarArchiver.addFile(artifact.getFile, "dependencies/" + artifact.getFile.getName)
          copyConfigDefinitions(artifact.getFile, jarArchiver)
        }
        else
          getLog.warn("Unkown artifact type " + artifact.getType)
    }
  }

  private def copyConfigDefinitions(file: File, jarArchiver: JarArchiver) {
    JarFiles.withJarFile(file) { jarFile =>
      for {
        entry <- jarFile.entries()
        name = entry.getName
        if name.startsWith("configdefinitions/") && name.endsWith(".def")

      } copyConfigDefinition(jarFile, entry, jarArchiver)
    }
  }

  private def copyConfigDefinition(jarFile: JarFile, entry: ZipEntry, jarArchiver: JarArchiver) {
    JarFiles.withInputStream(jarFile, entry) { input =>
      val defPath = entry.getName.replace("/", File.separator)
      val destinationFile = new File(project.getBuild.getOutputDirectory, defPath)
      destinationFile.getParentFile.mkdirs()

      Files.withFileOutputStream(destinationFile) { output =>
        output.getChannel.transferFrom(Channels.newChannel(input), 0, Long.MaxValue)
      }
      jarArchiver.addFile(destinationFile, entry.getName)
    }
  }

  private def build = project.getBuild
}
