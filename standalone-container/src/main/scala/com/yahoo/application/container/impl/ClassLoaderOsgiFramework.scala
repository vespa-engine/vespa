// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container.impl

import java.io.InputStream
import java.net.{URL, URLClassLoader}
import java.util
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.JarFile
import java.util.{Collections, Dictionary, Hashtable}

import com.yahoo.container.standalone.StandaloneContainerApplication
import com.yahoo.jdisc.application.{OsgiFramework, OsgiHeader}
import org.osgi.framework._
import org.osgi.framework.wiring._
import org.osgi.resource.{Capability, Requirement, Wire}

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

/**
 * A (mock) OSGI implementation which loads classes from the system classpath
 * 
 * @author tonytv
 */
final class ClassLoaderOsgiFramework extends OsgiFramework {
  private val bundleLocations = new ArrayBuffer[URL]
  private val bundleList = ArrayBuffer[Bundle](SystemBundleImpl)
  private var classLoader: ClassLoader = null

  private val nextBundleId = new AtomicInteger(1)

  override def installBundle(bundleLocation: String) = {
    if (bundleLocation != "") {
      val url = new URL(bundleLocation)
      bundleLocations += url
      bundleList += new JarBundleImpl(url)
    }

    bundles()
  }

  def getClassLoader = {
    if (bundleLocations.isEmpty) {
      getClass.getClassLoader
    } else {
      if(classLoader == null)
        classLoader = new URLClassLoader(bundleLocations.toArray, getClass.getClassLoader)

      classLoader
    }
  }

  override def startBundles(bundles: util.List[Bundle], privileged: Boolean) {}

  override def refreshPackages() {}

  override def bundleContext():BundleContext = BundleContextImpl

  override def bundles() = bundleList.asJava

  override def start() {}

  override def stop() {}

  private abstract class BundleImpl extends Bundle {
    override def getState = Bundle.ACTIVE

    override def start(options: Int) {}
    override def start() {}
    override def stop(options: Int) {}
    override def stop() {}
    override def update(input: InputStream) {}
    override def update() {}
    override def uninstall() {}

    override def getHeaders(locale: String) = getHeaders

    override def getSymbolicName = ClassLoaderOsgiFramework.this.getClass.getName
    override def getLocation = getSymbolicName

    override def getRegisteredServices = Array[ServiceReference[_]]()
    override def getServicesInUse = getRegisteredServices

    override def hasPermission(permission: Any) = true

    override def getResource(name: String) = getClassLoader.getResource(name)
    override def loadClass(name: String) = getClassLoader.loadClass(name)
    override def getResources(name: String) = getClassLoader.getResources(name)

    override def getEntryPaths(path: String) = throw new UnsupportedOperationException
    override def getEntry(path: String) = throw new UnsupportedOperationException
    override def findEntries(path: String, filePattern: String, recurse: Boolean) = throw new UnsupportedOperationException

    override def getLastModified = 1L

    override def getBundleContext = throw new UnsupportedOperationException
    override def getSignerCertificates(signersType: Int) = Collections.emptyMap()

    override def adapt[A](`type`: Class[A]): A = {
      if (`type` == classOf[BundleRevision]) BundleRevisionImpl.asInstanceOf[A]
      else if (`type` == classOf[BundleWiring]) BundleWiringImpl.asInstanceOf[A]
      else null.asInstanceOf[A]
    }

    override def getDataFile(filename: String) = null
    override def compareTo(o: Bundle) = getBundleId compareTo o.getBundleId
  }

  private object BundleRevisionImpl extends BundleRevision {
    override def getSymbolicName: String = this.getClass.getName
    override def getDeclaredRequirements(p1: String): util.List[BundleRequirement] = throw new UnsupportedOperationException
    override def getVersion: Version = Version.emptyVersion
    override def getWiring: BundleWiring = BundleWiringImpl
    override def getDeclaredCapabilities(p1: String): util.List[BundleCapability] = throw new UnsupportedOperationException
    override def getTypes: Int = 0
    override def getBundle: Bundle = throw new UnsupportedOperationException
    override def getCapabilities(p1: String): util.List[Capability] = throw new UnsupportedOperationException
    override def getRequirements(p1: String): util.List[Requirement] = throw new UnsupportedOperationException
  }

  private object BundleWiringImpl extends BundleWiring {
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
    override def listResources(p1: String, p2: String, p3: Int): util.Collection[String] = ???
    override def getClassLoader: ClassLoader = ClassLoaderOsgiFramework.this.getClassLoader
    override def getRequirements(p1: String): util.List[BundleRequirement] = ???
    override def getResource: BundleRevision = ???
    override def getBundle: Bundle = ???
  }

  private object SystemBundleImpl extends BundleImpl {
    override val getBundleId = 0L
    override def getVersion = Version.emptyVersion
    override def getHeaders: Dictionary[String, String] = new Hashtable[String, String](Map(OsgiHeader.APPLICATION -> classOf[StandaloneContainerApplication].getName).asJava)
  }


  private class JarBundleImpl(location: URL) extends BundleImpl {
    override val getBundleId = nextBundleId.getAndIncrement.asInstanceOf[Long]

    private val headers = retrieveHeaders(location)

    override def getHeaders: Dictionary[String, String] = headers
    override val getSymbolicName = headers.get("Bundle-SymbolicName")
    override val getVersion = Version.parseVersion(headers.get("Bundle-Version"))


    private def retrieveHeaders(location: URL) = {
      val jarFile = new JarFile(location.getFile)
      try {
        val attributes = jarFile.getManifest.getMainAttributes
        new Hashtable[String, String](attributes.entrySet().asScala.map( entry => entry.getKey.toString -> entry.getValue.toString).toMap.asJava)
      } finally {
        jarFile.close()
      }
    }
  }

  private object BundleContextImpl extends BundleContext {
    private val bundleImpl = SystemBundleImpl

    override def getProperty(key: String) = null
    override def getBundle = bundleImpl
    override def installBundle(location: String, input: InputStream) = throw new UnsupportedOperationException
    override def installBundle(location: String) = throw new UnsupportedOperationException

    override def getBundle(id: Long) = bundleImpl
    override def getBundles = Array(bundleImpl)
    override def getBundle(location: String) = bundleImpl

    override def addServiceListener(listener: ServiceListener, filter: String) {}
    override def addServiceListener(listener: ServiceListener) {}
    override def removeServiceListener(listener: ServiceListener) {}
    override def addBundleListener(listener: BundleListener) {}
    override def removeBundleListener(listener: BundleListener) {}

    override def addFrameworkListener(listener: FrameworkListener) {}
    override def removeFrameworkListener(listener: FrameworkListener) {}

    override def registerService(clazzes: Array[String], service: Any, properties: Dictionary[String, _]) = throw new UnsupportedOperationException
    override def registerService(clazz: String, service: Any, properties: Dictionary[String, _]) = null
    override def registerService[S](clazz: Class[S], service: S, properties: Dictionary[String, _]) = throw new UnsupportedOperationException
    override def getServiceReferences(clazz: String, filter: String) = throw new UnsupportedOperationException
    override def getAllServiceReferences(clazz: String, filter: String) = throw new UnsupportedOperationException
    override def getServiceReference(clazz: String) = throw new UnsupportedOperationException
    override def getServiceReference[S](clazz: Class[S]) = throw new UnsupportedOperationException
    override def getServiceReferences[S](clazz: Class[S], filter: String) = Collections.emptyList()
    override def getService[S](reference: ServiceReference[S]) = throw new UnsupportedOperationException
    override def ungetService(reference: ServiceReference[_]) = throw new UnsupportedOperationException
    override def getDataFile(filename: String) = throw new UnsupportedOperationException
    override def createFilter(filter: String) = throw new UnsupportedOperationException

    override def registerService[S](aClass: Class[S], serviceFactory: ServiceFactory[S], dictionary: Dictionary[String, _]): ServiceRegistration[S] = throw new UnsupportedOperationException
    override def getServiceObjects[S](serviceReference: ServiceReference[S]): ServiceObjects[S] = throw new UnsupportedOperationException
  }

}
