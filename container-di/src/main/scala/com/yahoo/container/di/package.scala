// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container

import java.lang.reflect.Type

import com.google.inject.Key
import com.yahoo.config.ConfigInstance
import com.yahoo.vespa.config.ConfigKey

import scala.language.implicitConversions

/**
 *
 * @author gjoranv
 * @author tonytv
 */
package object di {
  type ConfigKeyT = ConfigKey[_ <: ConfigInstance]
  type GuiceInjector = com.google.inject.Injector
  type JavaAnnotation = java.lang.annotation.Annotation

  def createKey(instanceType: Type, annotation: JavaAnnotation) = {
    {if (annotation == null)
      Key.get(instanceType)
    else
      Key.get(instanceType, annotation)
    }.asInstanceOf[Key[AnyRef]]
  }

  implicit def makeClassCovariant[SUB, SUPER >: SUB](clazz: Class[SUB]) : Class[SUPER] = {
    clazz.asInstanceOf[Class[SUPER]]
  }

  def removeStackTrace(exception: Throwable): Throwable = {
    if (preserveStackTrace) exception
    else {
      exception.setStackTrace(Array())
      exception
    }
  }

  //For debug purposes only
  val preserveStackTrace: Boolean = Option(System.getProperty("jdisc.container.preserveStackTrace")).filterNot(_.isEmpty).isDefined
}
