// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor

import com.yahoo.jrt.slobrok.api.Mirror.Entry
import com.yahoo.jrt.slobrok.api.{Mirror, SlobrokList}
import com.yahoo.jrt.{Transport, Supervisor}
import com.yahoo.vespa.service.monitor.SlobrokMonitor._
import scala.collection.convert.wrapAsJava._

/**
 * @author bakksjo
 */
class SlobrokMonitor {
  private val supervisor: Supervisor = new Supervisor(new Transport())
  private val slobrokList = new SlobrokList()
  private val mirror = new Mirror(supervisor, slobrokList)

  def setSlobrokConnectionSpecs(slobrokConnectionSpecs: Traversable[String]): Unit = {
    val slobrokConnectionSpecsJavaList: java.util.List[String] = slobrokConnectionSpecs.toList
    slobrokList.setup(slobrokConnectionSpecsJavaList.toArray(new Array[java.lang.String](0)))
  }

  def getRegisteredServices(): Map[SlobrokServiceName, SlobrokServiceSpec] = {
    if (!mirror.ready()) {
      return Map()
    }
    val mirrorEntries: Array[Entry] = mirror.lookup("**")
    mirrorEntries.map { mirrorEntry =>
      (SlobrokServiceName(mirrorEntry.getName), SlobrokServiceSpec(mirrorEntry.getSpec))
    }.toMap
  }

  def isRegistered(serviceName: SlobrokServiceName): Boolean = {
    mirror.lookup(serviceName.s).length != 0
  }

  def shutdown(): Unit = {
    mirror.shutdown()
  }
}

object SlobrokMonitor {
  case class SlobrokServiceName(s: String)
  case class SlobrokServiceSpec(s: String)
}
