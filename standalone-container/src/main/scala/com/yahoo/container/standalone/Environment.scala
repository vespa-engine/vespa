// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.standalone

/**
 * @author tonytv
 * TODO: copied from standalone-container. Move to separate lib module instead.
 */
object Environment {
  def optionalInstallVariable(name: String) = {
    env(name.replace(".", "__")).
      orElse(systemProperty(name)) //for unit testing
  }

  def installVariable(name: String) = {
    optionalInstallVariable(name).
      getOrElse {
      throw new IllegalStateException("Environment variable not set: " + name)
    }
  }

  def env(name: String) = Option(System.getenv(name))
  def systemProperty(name: String) = Option(System.getProperty(name))
}
