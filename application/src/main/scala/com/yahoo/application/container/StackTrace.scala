// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container

/**
 * @author tonytv
 */
object StackTrace {
  private val jdiscContainerStartMarker = new StackTraceElement("============= JDiscContainer =============", "start", null, -1)

  def filterLogAndDieToJDiscInit(throwable: Throwable) {
    val stackTrace = throwable.getStackTrace

    for {
      top <- stackTrace.headOption
      if !preserveStackTrace
      if top.getClassName == classOf[com.yahoo.protect.Process].getName
      index = stackTrace.lastIndexWhere(_.getClassName == classOf[JDisc].getName)
      if (index != -1)
    } throwable.setStackTrace(jdiscContainerStartMarker +: stackTrace.drop(index))
  }

  //TODO: combine with version in container/di
  val preserveStackTrace: Boolean = Option(System.getProperty("jdisc.container.preserveStackTrace")).filterNot(_.isEmpty).isDefined
}
