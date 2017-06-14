// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.componentgraph.core

import com.yahoo.component.{ComponentSpecification, ComponentId}
import com.yahoo.container.di.Osgi.RelativePath
import com.yahoo.container.di.config.RestApiContext
import org.osgi.framework.wiring.BundleWiring
import scala.collection.JavaConverters._

import scala.collection.convert.wrapAsJava._
import RestApiContext.BundleInfo
import JerseyNode._
import com.yahoo.container.di.Osgi
import org.osgi.framework.Bundle
import java.net.URL

/**
 * Represents an instance of RestApiContext
 *
 * @author gjoranv
 * @author tonytv
 * @since 5.15
 */
class JerseyNode(componentId: ComponentId,
                 override val configId: String,
                 clazz: Class[_ <: RestApiContext],
                 osgi: Osgi)
  extends ComponentNode(componentId, configId, clazz) {


  override protected def newInstance(): RestApiContext = {
    val instance = super.newInstance()
    val restApiContext = instance.asInstanceOf[RestApiContext]

    val bundles = restApiContext.bundlesConfig.bundles().asScala
    bundles foreach ( bundleConfig => {
        val bundleClasses = osgi.getBundleClasses(
          ComponentSpecification.fromString(bundleConfig.spec()),
          bundleConfig.packages().asScala.toSet)

        restApiContext.addBundle(
          createBundleInfo(bundleClasses.bundle, bundleClasses.classEntries))
    })

    componentsToInject foreach (
      component =>
        restApiContext.addInjectableComponent(component.instanceKey, component.componentId, component.newOrCachedInstance()))

    restApiContext
  }

  override def equals(other: Any): Boolean = {
    super.equals(other) && (other match {
      case that: JerseyNode => componentsToInject == that.componentsToInject
      case _ => false
    })
  }

}

private[core]
object JerseyNode {
  val WebInfUrl = "WebInfUrl"

  def createBundleInfo(bundle: Bundle, classEntries: Iterable[RelativePath]): BundleInfo = {

    val bundleInfo = new BundleInfo(bundle.getSymbolicName,
                                    bundle.getVersion,
                                    bundle.getLocation,
                                    webInfUrl(bundle),
                                    bundle.adapt(classOf[BundleWiring]).getClassLoader)

    bundleInfo.setClassEntries(classEntries)
    bundleInfo
  }


  private def getBundle(osgi: Osgi, bundleSpec: String): Bundle = {

    val bundle = osgi.getBundle(ComponentSpecification.fromString(bundleSpec))
    if (bundle == null)
      throw new IllegalArgumentException("Bundle not found: " + bundleSpec)
    bundle
  }

  private def webInfUrl(bundle: Bundle): URL = {
    val strWebInfUrl = bundle.getHeaders.get(WebInfUrl)

    if (strWebInfUrl == null) null
    else bundle.getEntry(strWebInfUrl)
  }

}
