// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.mojo


import scala.collection.JavaConversions._
import org.apache.maven.project.MavenProject
import org.apache.maven.artifact.Artifact


/**
 * @author tonytv
 */
object Artifacts {
  def getArtifacts(project : MavenProject) = {
    type artifactSet = java.util.Set[Artifact]
    val artifacts = project.getArtifacts.asInstanceOf[artifactSet].groupBy(_.getScope)

    def isTypeJar(artifact : Artifact) = artifact.getType == "jar"
    def getByScope(scope: String) =
      artifacts.getOrElse(scope, Iterable.empty).partition(isTypeJar)


    val (jarArtifactsToInclude, nonJarArtifactsToInclude) = getByScope(Artifact.SCOPE_COMPILE)
    val (jarArtifactsProvided,   nonJarArtifactsProvided)  = getByScope(Artifact.SCOPE_PROVIDED)

    (jarArtifactsToInclude, jarArtifactsProvided, nonJarArtifactsToInclude ++ nonJarArtifactsProvided)
  }

  def getArtifactsToInclude(project: MavenProject) = getArtifacts(project)._1
}
