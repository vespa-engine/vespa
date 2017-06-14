// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.test.jars.jersey.resources

import javax.ws.rs.core.MediaType
import javax.ws.rs.{Produces, GET}

import scala.reflect.ClassTag

/**
 * @author tonytv
 */
class TestResourceBase {
  @GET
  @Produces(Array(MediaType.TEXT_PLAIN))
  def get() = TestResourceBase.content(getClass)
}

object TestResourceBase {
  def content(clazz: Class[_ <: TestResourceBase]): String =
    "Response from " + clazz.getName

  def content[T <: TestResourceBase](implicit classTag: ClassTag[T]): String = {
    val clazz = classTag.runtimeClass.asInstanceOf[Class[_ <: TestResourceBase]]
    content(clazz)
  }
}
