// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container.jersey

import java.nio.file.{Files, Path, Paths}
import javax.ws.rs.core.UriBuilder

import com.yahoo.application.Networking
import com.yahoo.application.container.JDiscTest._
import com.yahoo.container.test.jars.jersey.resources.TestResourceBase
import com.yahoo.container.test.jars.jersey.{resources => jarResources}
import com.yahoo.osgi.maven.ProjectBundleClassPaths
import com.yahoo.osgi.maven.ProjectBundleClassPaths.BundleClasspathMapping
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.util.EntityUtils
import org.hamcrest.CoreMatchers.is
import org.junit.Assert._
import org.junit.Test

import scala.collection.JavaConverters._
import scala.io.Source

/**
 * @author tonytv
 */
class JerseyTest {
  type TestResourceClass = Class[_ <: TestResourceBase]

  val testJar = Paths.get("target/test-jars/jersey-resources.jar")
  val testClassesDirectory = "target/test-classes"
  val bundleSymbolicName = "myBundle"

  val classPathResources = Set(
    classOf[resources.TestResource],
    classOf[resources.nestedpackage1.NestedTestResource1],
    classOf[resources.nestedpackage2.NestedTestResource2])

  val jarFileResources = Set(
    classOf[jarResources.TestResource],
    classOf[com.yahoo.container.test.jars.jersey.resources.nestedpackage1.NestedTestResource1],
    classOf[com.yahoo.container.test.jars.jersey.resources.nestedpackage2.NestedTestResource2])

  @Test
  def jersey_resources_on_classpath_can_be_invoked_from_application(): Unit = {
    saveMainBundleClassPathMappings(testClassesDirectory)

    with_jersey_resources() { httpGetter =>
      assertResourcesResponds(classPathResources, httpGetter)
    }
  }

  @Test
  def jersey_resources_in_provided_dependencies_can_be_invoked_from_application(): Unit = {
    val providedDependency = new BundleClasspathMapping(bundleSymbolicName, List(testClassesDirectory).asJava)

    save(new ProjectBundleClassPaths(
      new BundleClasspathMapping("main", List().asJava),
      List(providedDependency).asJava))

    with_jersey_resources() { httpGetter =>
      assertResourcesResponds(classPathResources, httpGetter)
    }
  }

  @Test
  def jersey_resource_on_classpath_can_be_filtered_using_packages(): Unit = {
    saveMainBundleClassPathMappings(testClassesDirectory)

    with_jersey_resources(
      packagesToScan = List(
        "com.yahoo.application.container.jersey.resources",
        "com.yahoo.application.container.jersey.resources.nestedpackage1"))
    { httpGetter =>
      val nestedResource2 = classOf[resources.nestedpackage2.NestedTestResource2]

      assertDoesNotRespond(nestedResource2, httpGetter)
      assertResourcesResponds(classPathResources - nestedResource2, httpGetter)
    }
  }

  @Test
  def jersey_resource_in_jar_can_be_invoked_from_application(): Unit = {
    saveMainBundleJarClassPathMappings(testJar)

    with_jersey_resources() { httpGetter =>
      assertResourcesResponds(jarFileResources, httpGetter)
    }
  }

  @Test
  def jersey_resource_in_jar_can_be_filtered_using_packages(): Unit = {
    saveMainBundleJarClassPathMappings(testJar)

    with_jersey_resources(
      packagesToScan = List(
        "com.yahoo.container.test.jars.jersey.resources",
        "com.yahoo.container.test.jars.jersey.resources.nestedpackage1"))
    { httpGetter =>
      val nestedResource2 = classOf[com.yahoo.container.test.jars.jersey.resources.nestedpackage2.NestedTestResource2]

      assertDoesNotRespond(nestedResource2, httpGetter)
      assertResourcesResponds(jarFileResources - nestedResource2, httpGetter)
    }
  }

  def with_jersey_resources(packagesToScan: List[String] = List())( f: HttpGetter => Unit): Unit = {
    val packageElements = packagesToScan.map { p =>  <package>{p}</package>}

    using(fromServicesXml(
      <services>
        <jdisc version="1.0" id="default" jetty="true">
          <rest-api path="rest-api" jersey2="true">
            <components bundle={bundleSymbolicName}>
              { packageElements }
            </components>
          </rest-api>
          <http>
            <server id="mainServer" port="0" />
          </http>
        </jdisc>
      </services>,
      Networking.enable)) { jdisc =>


      def httpGetter(path: HttpPath) = {
        val client = HttpClientBuilder.create().build()
        client.execute(new HttpGet(s"http://localhost:$getListenPort/rest-api/${path.stripPrefix("/")}"))
      }

      f(httpGetter)
    }
  }

  def assertResourcesResponds(resourceClasses: Traversable[TestResourceClass], httpGetter: HttpGetter): Unit = {
    for (resource <- resourceClasses) {
      val response = httpGetter(path(resource))
      assertThat(s"Failed sending response to $resource", response.getStatusLine.getStatusCode, is(200))

      val content = Source.fromInputStream(response.getEntity.getContent).mkString
      assertThat(content, is(TestResourceBase.content(resource)))
    }
  }

  def assertDoesNotRespond(resourceClass: TestResourceClass, httpGetter: HttpGetter): Unit = {
    val response = httpGetter(path(resourceClass))
    assertThat(response.getStatusLine.getStatusCode, is(404))
    EntityUtils.consume(response.getEntity)
  }

  def saveMainBundleJarClassPathMappings(jarFile: Path): Unit = {
    assertTrue(s"Couldn't find file $jarFile, please remember to run mvn process-test-resources first.", Files.isRegularFile(jarFile))
    saveMainBundleClassPathMappings(jarFile.toAbsolutePath.toString)
  }

  def saveMainBundleClassPathMappings(classPathElement: String): Unit = {
    val mainBundleClassPathMappings = new BundleClasspathMapping(bundleSymbolicName, List(classPathElement).asJava)
    save(new ProjectBundleClassPaths(mainBundleClassPathMappings, List().asJava))
  }

  def save(projectBundleClassPaths: ProjectBundleClassPaths): Unit = {
    val path = Paths.get(testClassesDirectory).resolve(ProjectBundleClassPaths.CLASSPATH_MAPPINGS_FILENAME)
    ProjectBundleClassPaths.save(path, projectBundleClassPaths)
  }

  def path(resourceClass: TestResourceClass) = {
    UriBuilder.fromResource(resourceClass).build().toString
  }

  type HttpPath = String
  type HttpGetter = HttpPath => HttpResponse
}
