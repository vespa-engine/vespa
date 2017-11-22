// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.servlet.jersey

import java.io.{IOException, InputStream}

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider
import com.yahoo.container.di.componentgraph.Provider
import com.yahoo.container.di.config.RestApiContext
import com.yahoo.container.di.config.RestApiContext.BundleInfo
import com.yahoo.container.jaxrs.annotation.Component
import com.yahoo.container.servlet.jersey.util.ResourceConfigUtil.registerComponent
import org.eclipse.jetty.servlet.ServletHolder
import org.glassfish.hk2.api.{InjectionResolver, TypeLiteral}
import org.glassfish.hk2.utilities.Binder
import org.glassfish.hk2.utilities.binding.AbstractBinder
import org.glassfish.jersey.media.multipart.MultiPartFeature
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.servlet.ServletContainer
import org.objectweb.asm.ClassReader

import scala.collection.JavaConverters._
import scala.util.control.Exception


/**
 * @author tonytv
 */
class JerseyServletProvider(restApiContext: RestApiContext) extends Provider[ServletHolder] {
  private val jerseyServletHolder = new ServletHolder(new ServletContainer(resourceConfig(restApiContext)))

  private def resourceConfig(restApiContext: RestApiContext) = {
    val resourceConfig = ResourceConfig.forApplication(
      new JerseyApplication(resourcesAndProviders(restApiContext.getBundles.asScala)))

    registerComponent(resourceConfig, componentInjectorBinder(restApiContext))
    registerComponent(resourceConfig, jacksonDatatypeJdk8Provider)
    resourceConfig.register(classOf[MultiPartFeature])

    resourceConfig
  }

  def resourcesAndProviders(bundles: Traversable[BundleInfo]) =
    (for {
      bundle <- bundles.view
      classEntry <- bundle.getClassEntries.asScala
      className <- detectResourceOrProvider(bundle.classLoader, classEntry)
    } yield loadClass(bundle.symbolicName, bundle.classLoader, className)).toSet


  def detectResourceOrProvider(bundleClassLoader: ClassLoader, classEntry: String): Option[String] = {
    using(getResourceAsStream(bundleClassLoader, classEntry)) { inputStream =>
      val visitor = ResourceOrProviderClassVisitor.visit(new ClassReader(inputStream))
      visitor.getJerseyClassName
    }
  }

  private def getResourceAsStream(bundleClassLoader: ClassLoader, classEntry: String) = {
    bundleClassLoader.getResourceAsStream(classEntry) match {
      case null => throw new RuntimeException(s"No entry $classEntry in bundle $bundleClassLoader")
      case stream => stream
    }

  }

  def using[T <: InputStream, R](stream: T)(f: T => R): R = {
    try {
      f(stream)
    } finally {
      Exception.ignoring(classOf[IOException]) {
        stream.close()
      }
    }
  }

  def loadClass(bundleSymbolicName: String, classLoader: ClassLoader, className: String) = {
    try {
      classLoader.loadClass(className)
    } catch {
      case e: Exception => throw new RuntimeException(s"Failed loading class $className from bundle $bundleSymbolicName", e)
    }
  }

  def componentInjectorBinder(restApiContext: RestApiContext): Binder = {
    val componentGraphProvider = new ComponentGraphProvider(restApiContext.getInjectableComponents.asScala)
    val componentAnnotationType = new TypeLiteral[InjectionResolver[Component]] {}

    new AbstractBinder {
      override def configure() {
        bind(componentGraphProvider).to(componentAnnotationType)
      }
    }
  }

  def jacksonDatatypeJdk8Provider: JacksonJaxbJsonProvider = {
    val provider = new JacksonJaxbJsonProvider()
    provider.setMapper(
      new ObjectMapper()
        .registerModule(new Jdk8Module)
        .registerModule(new JavaTimeModule))
    provider
  }

  override def get() = jerseyServletHolder
  override def deconstruct() {}
}

