// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.mojo

import java.io.File
import java.util.jar.{Attributes, JarEntry, JarFile}
import java.util.regex.Pattern

import com.yahoo.container.plugin.bundle.AnalyzeBundle
import com.yahoo.container.plugin.classanalysis.{Analyze, ExportPackageAnnotation, PackageTally}
import com.yahoo.container.plugin.mojo.GenerateOsgiManifestMojo._
import com.yahoo.container.plugin.osgi.ExportPackages
import com.yahoo.container.plugin.osgi.ExportPackages.Export
import com.yahoo.container.plugin.osgi.ImportPackages.Import
import com.yahoo.container.plugin.osgi.{ExportPackageParser, ExportPackages, ImportPackages}
import com.yahoo.container.plugin.util.Files.allDescendantFiles
import com.yahoo.container.plugin.util.IO.withFileOutputStream
import com.yahoo.container.plugin.util.Iteration.toStream
import com.yahoo.container.plugin.util.JarFiles.{withInputStream, withJarFile}
import com.yahoo.container.plugin.util.Strings
import org.apache.maven.artifact.Artifact
import org.apache.maven.plugin.{AbstractMojo, MojoExecutionException, MojoFailureException}
import org.apache.maven.plugins.annotations.{Mojo, Parameter, ResolutionScope}
import org.apache.maven.project.MavenProject

import scala.collection.immutable.Map


/**
 * @author  tonytv
 */
@Mojo(name = "generate-osgi-manifest", requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
class GenerateOsgiManifestMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project}")
  var project: MavenProject = null

  @Parameter
  var discApplicationClass: String = null

  @Parameter
  var discPreInstallBundle: String = null

  @Parameter(alias = "Bundle-Version", defaultValue = "${project.version}")
  var bundleVersion: String = null

  @Parameter(alias = "Bundle-SymbolicName", defaultValue = "${project.artifactId}")
  var bundleSymbolicName: String = null

  @Parameter(alias = "Bundle-Activator")
  var bundleActivator: String = null

  @Parameter(alias = "X-JDisc-Privileged-Activator")
  var jdiscPrivilegedActivator: String = null

  @Parameter(alias = "X-Config-Models")
  var configModels: String = null

  @Parameter(alias = "Import-Package")
  var importPackage: String = null

  @Parameter(alias = "WebInfUrl")
  var webInfUrl: String = null

  @Parameter(alias = "Main-Class")
  var mainClass: String = null

  @Parameter(alias = "X-Jersey-Binding")
  var jerseyBinding: String = null

  case class PackageInfo(name : String, exportAnnotation : Option[ExportPackageAnnotation])

  def execute() {
    try {
      val (jarArtifactsToInclude, jarArtifactsProvided, nonJarArtifacts) = Artifacts.getArtifacts(project)
      warnOnUnsupportedArtifacts(nonJarArtifacts)

      val publicPackagesFromProvidedJars = AnalyzeBundle.publicPackagesAggregated(jarArtifactsProvided.map(_.getFile))
      val includedJarPackageTally = definedPackages(jarArtifactsToInclude)

      val projectPackageTally = analyzeProjectClasses()

      val pluginPackageTally = projectPackageTally.combine(includedJarPackageTally)

      warnIfPackagesDefinedOverlapsGlobalPackages(projectPackageTally.definedPackages ++ includedJarPackageTally.definedPackages,
        publicPackagesFromProvidedJars.globals)

      if (getLog.isDebugEnabled) {
        getLog.debug("Referenced packages = " + pluginPackageTally.referencedPackages)
        getLog.debug("Defined packages = " + pluginPackageTally.definedPackages)
        getLog.debug("Exported packages of dependencies = " +
          publicPackagesFromProvidedJars.exports.map(e => (e.packageNames, e.version getOrElse "")).toSet )
      }

      val calculatedImports = ImportPackages.calculateImports(
        pluginPackageTally.referencedPackages,
        pluginPackageTally.definedPackages,
        ExportPackages.exportsByPackageName(publicPackagesFromProvidedJars.exports))

      val manualImports = emptyToNone(importPackage) map getManualImports getOrElse Map()

      createManifestFile(new File(project.getBuild.getOutputDirectory),
        manifestContent(
          project,
          jarArtifactsToInclude,
          manualImports,
          (calculatedImports -- manualImports.keys).values.toSet,
          pluginPackageTally))

    } catch {
      case e: MojoFailureException => throw e
      case e: MojoExecutionException => throw e
      case e: Exception => throw new MojoExecutionException("Failed generating osgi manifest.", e)
    }
  }

  //TODO: Tell which dependency overlaps
  private def warnIfPackagesDefinedOverlapsGlobalPackages(internalPackages: Set[String], globalPackages: List[String]) {
    val overlap = internalPackages intersect globalPackages.toSet
    if (overlap.nonEmpty)
      throw new MojoExecutionException(
        "The following packages are both global and included in the bundle:\n%s".format(overlap map ("   " + _) mkString ("\n")))
  }


  def osgiExportPackages(exportedPackages: Map[String, ExportPackageAnnotation]): Iterable[String] = {
    for ((name, annotation) <- exportedPackages)
      yield name + ";version=" + annotation.osgiVersion
  }

  def trimWhitespace(lines: Option[String]): String = {
    lines.getOrElse("").split(",").map(_.trim).mkString(",")
  }

  def manifestContent(project: MavenProject, jarArtifactsToInclude: Traversable[Artifact],
                      manualImports: Map[String, Option[String]], imports : Set[Import],
                       pluginPackageTally : PackageTally) = {
    Map[String, String](
      "Created-By" -> "vespa container maven plugin",
      "Bundle-ManifestVersion" -> "2",
      "Bundle-Name" -> project.getName,
      "Bundle-SymbolicName" -> bundleSymbolicName,
      "Bundle-Version" -> asBundleVersion(bundleVersion),
      "Bundle-Vendor" -> "Yahoo!",
      "Bundle-ClassPath" -> bundleClassPath(jarArtifactsToInclude),
      "Bundle-Activator" -> bundleActivator,
      "X-JDisc-Privileged-Activator" -> jdiscPrivilegedActivator,
      "Main-Class" -> mainClass,
      "X-JDisc-Application" -> discApplicationClass,
      "X-JDisc-Preinstall-Bundle" -> trimWhitespace(Option(discPreInstallBundle)),
      "X-Config-Models" -> configModels,
      "X-Jersey-Binding" -> jerseyBinding,
      "WebInfUrl" -> webInfUrl,
      "Import-Package" -> ((manualImports map asOsgiImport) ++ (imports map {_.asOsgiImport})).toList.sorted.mkString(","),
      "Export-Package" -> osgiExportPackages(pluginPackageTally.exportedPackages).toList.sorted.mkString(","))
      .filterNot  { case (key, value) => value == null || value.isEmpty }

  }

  def asOsgiImport(importSpec: (String, Option[String])) = importSpec match {
    case (packageName, Some(version)) => packageName + ";version=" + quote(version)
    case (packageName, None) => packageName
  }

  def quote(s: String) = '"' + s + '"'

  def createManifestFile(outputDirectory: File, manifestContent: Map[String, String]) {
    val manifest = toManifest(manifestContent)

    withFileOutputStream(new File(outputDirectory, JarFile.MANIFEST_NAME)) {
      outputStream =>
        manifest.write(outputStream)
    }
  }

  def toManifest(manifestContent: Map[String, String]) = {
    val manifest = new java.util.jar.Manifest
    val mainAttributes = manifest.getMainAttributes

    mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")
    for ((key, value) <- manifestContent)
      mainAttributes.putValue(key, value)

    manifest
  }

  private def bundleClassPath(artifactsToInclude : Traversable[Artifact]) =
    ("." +: artifactsToInclude.map(dependencyPath).toList).mkString(",")

  private def dependencyPath(artifact : Artifact) =
    "dependencies/" + artifact.getFile.getName

  private def asBundleVersion(projectVersion: String) = {
    require(projectVersion != null, "Missing project version.")

    val parts = projectVersion.split(Pattern.quote("-"), 2)
    val numericPart = parts.head.split('.').map(Strings.emptyStringTo("0")).padTo(3, "0").toList

    val majorMinorMicro = numericPart take 3
    majorMinorMicro.mkString(".")
  }

  private def warnOnUnsupportedArtifacts(nonJarArtifacts: Traversable[Artifact]) {
    val unsupportedArtifacts = nonJarArtifacts.toSet.filter(_.getType != "pom")

    for (artifact <- unsupportedArtifacts) {
      getLog.warn(s"Unsupported artifact '${artifact.getId}': Type '${artifact.getType}' is not supported. Please file a feature request.")
    }
  }

  private def analyzeProjectClasses() : PackageTally = {
    val outputDirectory = new File(project.getBuild.getOutputDirectory)

    val analyzedClasses = allDescendantFiles(outputDirectory).filter(file => isClassToAnalyze(file.getName)).
      map(Analyze.analyzeClass)

    PackageTally.fromAnalyzedClassFiles(analyzedClasses)
  }

  def definedPackages(jarArtifacts: Iterable[Artifact]) : PackageTally = {
    PackageTally.combine(
      for (jarArtifact <- jarArtifacts) yield {
        withJarFile(jarArtifact.getFile) { jarFile =>
          definedPackages(jarFile)
        }
      })
  }

  def definedPackages(jarFile: JarFile) = {
    val analyzedClasses =
      for {
        entry <- toStream(jarFile.entries())
        if !entry.isDirectory
        if isClassToAnalyze(entry.getName)
        metaData = analyzeClass(jarFile, entry)
      } yield metaData

    PackageTally.fromAnalyzedClassFiles(analyzedClasses)
  }

  def analyzeClass(jarFile : JarFile, entry : JarEntry) = {
    try {
       withInputStream(jarFile, entry)(Analyze.analyzeClass)
    } catch {
      case e : Exception =>
        throw new MojoExecutionException(
          "While analyzing the class '%s' in jar file '%s'".format(entry.getName, jarFile.getName),
          e)
    }
  }
}

object GenerateOsgiManifestMojo {
  def getManualImports(importPackage: String): Map[String, Option[String]] = {
    try {
      (for {
        importDirective <- parseImportPackages(importPackage)
        packageName <- importDirective.packageNames
      } yield packageName -> getVersionThrowOthers(importDirective.parameters)).
        toMap

    } catch {
      case e: Exception => throw new RuntimeException("Error in Import-Package:" + importPackage, e)
    }
  }

  def getVersionThrowOthers(parameters: List[ExportPackages.Parameter]): Option[String] = {
    parameters match {
      case List() => None
      case List(ExportPackages.Parameter("version", v)) => Some(v)
      case default => throw new RuntimeException("A single, optional version parameter expected, but got " + default)
    }
  }

  def parseImportPackages(importPackages: String): List[Export] = {
    ExportPackageParser.parseAll(importPackages) match {
      case ExportPackageParser.NoSuccess(msg, _) => throw new RuntimeException(msg)
      case ExportPackageParser.Success(packages, _) => packages
    }
  }

  def isClassToAnalyze(name: String): Boolean =
    name.endsWith(".class") && ! name.endsWith("module-info.class")

  def emptyToNone(str: String) =
    Option(str) map {_.trim} filterNot  {_.isEmpty}
}
