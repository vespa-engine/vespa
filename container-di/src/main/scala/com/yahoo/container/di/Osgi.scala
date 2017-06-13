// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di

import com.yahoo.config.FileReference
import com.yahoo.container.bundle.{MockBundle, BundleInstantiationSpecification}
import com.yahoo.container.di.Osgi.BundleClasses
import org.osgi.framework.Bundle
import com.yahoo.component.ComponentSpecification

/**
 *
 * @author gjoranv
 * @author tonytv
 */
trait Osgi {

  def getBundleClasses(bundle: ComponentSpecification, packagesToScan: Set[String]): BundleClasses = {
    BundleClasses(new MockBundle, List())
  }

  def useBundles(bundles: java.util.Collection[FileReference]) {
    println("useBundles " + bundles.toArray.mkString(", "))
  }

  def resolveClass(spec: BundleInstantiationSpecification): Class[AnyRef] = {
    println("resolving class " + spec.classId)
    Class.forName(spec.classId.getName).asInstanceOf[Class[AnyRef]]
  }

  def getBundle(spec: ComponentSpecification): Bundle = {
    println("resolving bundle " + spec)
    new MockBundle()
  }

}

object Osgi {
  type RelativePath = String //e.g. "com/yahoo/MyClass.class"
  case class BundleClasses(bundle: Bundle, classEntries: Iterable[RelativePath])
}
