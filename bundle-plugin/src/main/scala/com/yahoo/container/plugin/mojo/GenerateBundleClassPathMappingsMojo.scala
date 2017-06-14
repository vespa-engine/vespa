// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.mojo

import java.io.File
import java.nio.file.Paths

import com.google.common.base.Preconditions
import com.yahoo.container.plugin.bundle.AnalyzeBundle
import com.yahoo.vespa.scalalib.osgi.maven.ProjectBundleClassPaths
import ProjectBundleClassPaths.BundleClasspathMapping
import com.yahoo.vespa.scalalib.osgi.maven.ProjectBundleClassPaths
import org.apache.maven.artifact.Artifact
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.{ResolutionScope, Mojo, Parameter}
import org.apache.maven.project.MavenProject



/**
 * Generates mapping from Bundle-SymbolicName to classpath elements, e.g
 * myBundle -> List(.m2/repository/com/mylib/Mylib.jar, myBundleProject/target/classes)
 * The mapping in stored in a json file.
 * @author tonytv
 */
@Mojo(name = "generate-bundle-classpath-mappings", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
class GenerateBundleClassPathMappingsMojo extends AbstractMojo {
  @Parameter(defaultValue = "${project}")
  private var project: MavenProject = null

  //TODO: Combine with com.yahoo.container.plugin.mojo.GenerateOsgiManifestMojo.bundleSymbolicName
  @Parameter(alias = "Bundle-SymbolicName", defaultValue = "${project.artifactId}")
  private var bundleSymbolicName: String = null


  /* Sample output -- target/test-classes/bundle-plugin.bundle-classpath-mappings.json
  {
    "mainBundle": {
        "bundleSymbolicName": "bundle-plugin-test",
        "classPathElements": [
            "/Users/tonyv/Repos/vespa/bundle-plugin-test/target/classes",
            "/Users/tonyv/.m2/repository/com/yahoo/vespa/jrt/6-SNAPSHOT/jrt-6-SNAPSHOT.jar",
            "/Users/tonyv/.m2/repository/com/yahoo/vespa/annotations/6-SNAPSHOT/annotations-6-SNAPSHOT.jar"
        ]
    },
    "providedDependencies": [
        {
            "bundleSymbolicName": "jrt",
            "classPathElements": [
                "/Users/tonyv/.m2/repository/com/yahoo/vespa/jrt/6-SNAPSHOT/jrt-6-SNAPSHOT.jar"
            ]
        }
    ]
   }
  */
  override def execute(): Unit = {
    Preconditions.checkNotNull(bundleSymbolicName)

    val (embeddedArtifacts, providedJarArtifacts, _) = asLists(Artifacts.getArtifacts(project))

    val embeddedArtifactsFiles = embeddedArtifacts.map(_.getFile)

    val classPathElements =  (outputDirectory +: embeddedArtifactsFiles).map(_.getAbsolutePath)

    val classPathMappings = ProjectBundleClassPaths(
      mainBundle = BundleClasspathMapping(bundleSymbolicName, classPathElements),
      providedDependencies =  providedJarArtifacts flatMap createDependencyClasspathMapping
    )

    ProjectBundleClassPaths.save(
      testOutputPath.resolve(ProjectBundleClassPaths.classPathMappingsFileName),
      classPathMappings)
  }

  private def outputDirectory = new File(project.getBuild.getOutputDirectory)
  private def testOutputPath = Paths.get(project.getBuild.getTestOutputDirectory)

  /* TODO:
  * 1) add the dependencies of the artifact in the future(i.e. dependencies of dependencies)
  * or
  * 2) obtain bundles with embedded dependencies from the maven repository,
  *     and support loading classes from the nested jar files in those bundles.
  */
  def createDependencyClasspathMapping(artifact: Artifact): Option[BundleClasspathMapping] = {
    for (bundleSymbolicName <- bundleSymbolicNameForArtifact(artifact))
    yield BundleClasspathMapping(bundleSymbolicName, classPathElements = List(artifact.getFile.getAbsolutePath))
  }

  def bundleSymbolicNameForArtifact(artifact: Artifact): Option[String] = {
    if (artifact.getFile.getName.endsWith(".jar")) AnalyzeBundle.bundleSymbolicName(artifact.getFile)
    else Some(artifact.getArtifactId) //Not the best heuristic. The other alternatives are parsing the pom file or
                                      //storing information in target/classes when building the provided bundles.
  }

  def asLists[A](tuple: (Iterable[A], Iterable[A], Iterable[A])) =
    (tuple._1.toList, tuple._2.toList, tuple._3.toList)
}
