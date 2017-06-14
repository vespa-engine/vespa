// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.mojo

import java.io.File
import java.nio.channels.Channels
import java.util.jar.JarFile
import java.util.zip.ZipEntry

import com.yahoo.container.plugin.util.{Files, JarFiles}
import org.apache.maven.archiver.{MavenArchiveConfiguration, MavenArchiver}
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.{Component, Mojo, Parameter, ResolutionScope}
import org.apache.maven.project.MavenProject
import org.codehaus.plexus.archiver.Archiver
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

  @Component(role = classOf[Archiver], hint = "jar")
  var jarArchiver: JarArchiver = null

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

    addClassesDirectory()
    createArchive(jarFiles(withoutDependencies))
    project.getArtifact.setFile(jarFiles(withoutDependencies))

    addDependencies()
    createArchive(jarFiles(withDependencies))
  }

  private def jarFileInBuildDirectory(name: String)(jarSuffix: String) = {
    new File(build.getDirectory, name + jarSuffix)
  }

  private def addClassesDirectory() {
    val classesDirectory = new File(build.getOutputDirectory)
    if (classesDirectory.isDirectory) {
      jarArchiver.addDirectory(classesDirectory)
    }
  }

  private def createArchive(jarFile: File) {
    val mavenArchiver = new MavenArchiver
    mavenArchiver.setArchiver(jarArchiver)
    mavenArchiver.setOutputFile(jarFile)
    mavenArchiver.createArchive(project, archiveConfiguration)
  }

  private def addDependencies() {
    Artifacts.getArtifactsToInclude(project).foreach { artifact =>
        if (artifact.getType == "jar") {
          jarArchiver.addFile(artifact.getFile, "dependencies/" + artifact.getFile.getName)
          copyConfigDefinitions(artifact.getFile)
        }
        else
          getLog.warn("Unkown artifact type " + artifact.getType)
    }
  }

  private def copyConfigDefinitions(file: File) {
    JarFiles.withJarFile(file) { jarFile =>
      for {
        entry <- jarFile.entries()
        name = entry.getName
        if name.startsWith("configdefinitions/") && name.endsWith(".def")

      } copyConfigDefinition(jarFile, entry)
    }
  }

  private def copyConfigDefinition(jarFile: JarFile, entry: ZipEntry) {
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
