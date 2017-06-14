// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.servlet.jersey

import javax.ws.rs.core.Application

import scala.collection.convert.wrapAsJava._

/**
 * @author tonytv
 */
class JerseyApplication(resourcesAndProviderClasses: Set[Class[_]]) extends Application {
  private val classes: java.util.Set[Class[_]] = resourcesAndProviderClasses

  override def getClasses = classes
  override def getSingletons = super.getSingletons
}
