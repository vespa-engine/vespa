// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.bundle


import java.net.URL
import java.util

import org.osgi.framework.wiring._
import org.osgi.framework.{ServiceReference, Version, Bundle}
import java.io.InputStream
import java.util.{Collections, Hashtable, Dictionary}
import MockBundle._
import org.osgi.resource.{Capability, Wire, Requirement}

/**
 *
 * @author gjoranv
 * @since 5.15
 */
class MockBundle extends Bundle with BundleWiring {

  override def getState = Bundle.ACTIVE

  override def start(options: Int) {}
  override def start() {}
  override def stop(options: Int) {}
  override def stop() {}
  override def update(input: InputStream) {}
  override def update() {}
  override def uninstall() {}

  override def getHeaders(locale: String) = getHeaders

  override def getSymbolicName = SymbolicName
  override def getVersion: Version = BundleVersion
  override def getLocation = getSymbolicName
  override def getBundleId: Long = 0L

  override def getHeaders: Dictionary[String, String] = new Hashtable[String, String]()

  override def getRegisteredServices = Array[ServiceReference[_]]()
  override def getServicesInUse = getRegisteredServices

  override def hasPermission(permission: Any) = true

  override def getResource(name: String) = throw new UnsupportedOperationException
  override def loadClass(name: String) = throw new UnsupportedOperationException
  override def getResources(name: String) = throw new UnsupportedOperationException

  override def getEntryPaths(path: String) = throw new UnsupportedOperationException
  override def getEntry(path: String) = throw new UnsupportedOperationException
  override def findEntries(path: String, filePattern: String, recurse: Boolean) = Collections.emptyEnumeration()


  override def getLastModified = 1L

  override def getBundleContext = throw new UnsupportedOperationException
  override def getSignerCertificates(signersType: Int) = Collections.emptyMap()

  override def adapt[A](`type`: Class[A]) =
    `type` match {
      case MockBundle.bundleWiringClass => this.asInstanceOf[A]
      case _ => ???
    }

  override def getDataFile(filename: String) = null
  override def compareTo(o: Bundle) = getBundleId compareTo o.getBundleId


  //TODO: replace with mockito
  override def findEntries(p1: String, p2: String, p3: Int): util.List[URL] = ???
  override def getRequiredResourceWires(p1: String): util.List[Wire] = ???
  override def getResourceCapabilities(p1: String): util.List[Capability] = ???
  override def isCurrent: Boolean = ???
  override def getRequiredWires(p1: String): util.List[BundleWire] = ???
  override def getCapabilities(p1: String): util.List[BundleCapability] = ???
  override def getProvidedResourceWires(p1: String): util.List[Wire] = ???
  override def getProvidedWires(p1: String): util.List[BundleWire] = ???
  override def getRevision: BundleRevision = ???
  override def getResourceRequirements(p1: String): util.List[Requirement] = ???
  override def isInUse: Boolean = ???
  override def listResources(p1: String, p2: String, p3: Int): util.Collection[String] = Collections.emptyList()
  override def getClassLoader: ClassLoader = MockBundle.getClass.getClassLoader
  override def getRequirements(p1: String): util.List[BundleRequirement] = ???
  override def getResource: BundleRevision = ???
  override def getBundle: Bundle = ???
}

object MockBundle {
  val SymbolicName = "mock-bundle"
  val BundleVersion = new Version(1, 0, 0)

  val bundleWiringClass = classOf[BundleWiring]


}
