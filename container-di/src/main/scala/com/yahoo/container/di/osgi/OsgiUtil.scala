// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.osgi

import java.nio.file.{Files, Path, Paths}
import java.util.function.Predicate
import java.util.jar.{JarEntry, JarFile}
import java.util.logging.{Level, Logger}
import java.util.stream.Collectors

import com.yahoo.component.ComponentSpecification
import com.yahoo.container.di.Osgi.RelativePath
import com.yahoo.vespa.scalalib.arm.Using.using
import com.yahoo.vespa.scalalib.osgi.maven.ProjectBundleClassPaths
import com.yahoo.vespa.scalalib.osgi.maven.ProjectBundleClassPaths.BundleClasspathMapping
import org.osgi.framework.Bundle
import org.osgi.framework.wiring.BundleWiring

import com.google.common.io.Files.fileTreeTraverser

import scala.collection.convert.decorateAsScala._

import com.yahoo.vespa.scalalib.java.function.FunctionConverters._

/**
 * Tested by com.yahoo.application.container.jersey.JerseyTest
 * @author tonytv
 */
object OsgiUtil {
  private val log = Logger.getLogger(getClass.getName)
  private  val classFileTypeSuffix = ".class"

  def getClassEntriesInBundleClassPath(bundle: Bundle, packagesToScan: Set[String]) = {
    val bundleWiring = bundle.adapt(classOf[BundleWiring])

    def listClasses(path: String, recurse: Boolean): Iterable[RelativePath] = {
      val options =
        if (recurse) BundleWiring.LISTRESOURCES_LOCAL | BundleWiring.LISTRESOURCES_RECURSE
        else         BundleWiring.LISTRESOURCES_LOCAL

      bundleWiring.listResources(path, "*" + classFileTypeSuffix, options).asScala
    }

    if (packagesToScan.isEmpty) listClasses("/", recurse = true)
    else packagesToScan flatMap { packageName => listClasses(packageToPath(packageName), recurse = false) }
  }

  def getClassEntriesForBundleUsingProjectClassPathMappings(classLoader: ClassLoader,
                                                            bundleSpec: ComponentSpecification,
                                                            packagesToScan: Set[String]) = {
    classEntriesFrom(
      bundleClassPathMapping(bundleSpec, classLoader).classPathElements,
      packagesToScan)
  }

  private def bundleClassPathMapping(bundleSpec: ComponentSpecification,
                                     classLoader: ClassLoader): BundleClasspathMapping = {

    val projectBundleClassPaths = loadProjectBundleClassPaths(classLoader)

    if (projectBundleClassPaths.mainBundle.bundleSymbolicName == bundleSpec.getName) {
      projectBundleClassPaths.mainBundle
    } else {
      log.log(Level.WARNING, s"Dependencies of the bundle $bundleSpec will not be scanned. Please file a feature request if you need this" )
      matchingBundleClassPathMapping(bundleSpec, projectBundleClassPaths.providedDependencies)
    }
  }

  def matchingBundleClassPathMapping(bundleSpec: ComponentSpecification,
                                     providedBundlesClassPathMappings: List[BundleClasspathMapping]): BundleClasspathMapping = {
    providedBundlesClassPathMappings.
      find(_.bundleSymbolicName == bundleSpec.getName).
      getOrElse(throw new RuntimeException("No such bundle: " + bundleSpec))
  }

  private def loadProjectBundleClassPaths(classLoader: ClassLoader): ProjectBundleClassPaths = {
    val classPathMappingsFileLocation = classLoader.getResource(ProjectBundleClassPaths.classPathMappingsFileName)
    if (classPathMappingsFileLocation == null)
      throw new RuntimeException(s"Couldn't find ${ProjectBundleClassPaths.classPathMappingsFileName}  in the class path.")

    ProjectBundleClassPaths.load(Paths.get(classPathMappingsFileLocation.toURI))
  }

  private def classEntriesFrom(classPathEntries: List[String], packagesToScan: Set[String]): Iterable[RelativePath] = {
    val packagePathsToScan = packagesToScan map packageToPath

    classPathEntries.flatMap { entry =>
      val path = Paths.get(entry)
      if (Files.isDirectory(path)) classEntriesInPath(path, packagePathsToScan)
      else if (Files.isRegularFile(path) && path.getFileName.toString.endsWith(".jar")) classEntriesInJar(path, packagePathsToScan)
      else throw new RuntimeException("Unsupported path " + path + " in the class path")
    }
  }

  private def classEntriesInPath(rootPath: Path, packagePathsToScan: Traversable[String]): Traversable[RelativePath] = {
    def relativePathToClass(pathToClass: Path): RelativePath = {
      val relativePath = rootPath.relativize(pathToClass)
      relativePath.toString
    }

    val fileIterator =
      if (packagePathsToScan.isEmpty) fileTreeTraverser().preOrderTraversal(rootPath.toFile).asScala
      else packagePathsToScan.view flatMap  { packagePath =>  fileTreeTraverser().children(rootPath.resolve(packagePath).toFile).asScala }

    for {
      file <- fileIterator
      if file.isFile
      if file.getName.endsWith(classFileTypeSuffix)
    } yield relativePathToClass(file.toPath)
  }


  private def classEntriesInJar(jarPath: Path, packagePathsToScan: Set[String]): Traversable[RelativePath] = {
    def packagePath(name: String) = {
      name.lastIndexOf('/') match {
        case -1 => name
        case n => name.substring(0, n)
      }
    }

    val acceptedPackage: Predicate[String] =
      if (packagePathsToScan.isEmpty) (name: String) => true
      else (name: String) => packagePathsToScan(packagePath(name))

    using(new JarFile(jarPath.toFile)) { jarFile =>
      jarFile.stream().
        map[String] { entry: JarEntry => entry.getName}.
        filter { name: String => name.endsWith(classFileTypeSuffix)}.
        filter(acceptedPackage).
        collect(Collectors.toList()).
        asScala
    }
  }

  def packageToPath(packageName: String) = packageName.replaceAllLiterally(".", "/")
}
