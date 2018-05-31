// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di


import java.util.logging.{Level, Logger}

import com.yahoo.config.ConfigInstance
import com.yahoo.container.di.ConfigRetriever._
import com.yahoo.container.di.config.Subscriber
import com.yahoo.log.LogLevel.DEBUG

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.language.postfixOps

/**
 * @author Tony Vaagenes
 * @author gjoranv
 */
final class ConfigRetriever(bootstrapKeys: Set[ConfigKeyT],
                            subscribe: Set[ConfigKeyT] => Subscriber)
{
  require(!bootstrapKeys.isEmpty)

  private val bootstrapSubscriber: Subscriber = subscribe(bootstrapKeys)

  private var componentSubscriber: Subscriber = subscribe(Set())
  private var componentSubscriberKeys: Set[ConfigKeyT] = Set()


  @tailrec
  final def getConfigs(componentConfigKeys: Set[ConfigKeyT], leastGeneration: Long, restartOnRedeploy: Boolean = false): ConfigSnapshot = {
    require(componentConfigKeys intersect bootstrapKeys isEmpty)
    log.log(DEBUG, "getConfigs: " + componentConfigKeys)

    setupComponentSubscriber(componentConfigKeys ++ bootstrapKeys)

    getConfigsOptional(leastGeneration, restartOnRedeploy) match {
      case Some(snapshot) => resetComponentSubscriberIfBootstrap(snapshot); snapshot
      case None => getConfigs(componentConfigKeys, leastGeneration, restartOnRedeploy)
    }
  }

  private def getConfigsOptional(leastGeneration: Long, restartOnRedeploy: Boolean): Option[ConfigSnapshot] = {
    val newestComponentGeneration = componentSubscriber.waitNextGeneration()
    log.log(DEBUG, s"getConfigsOptional: new component generation: $newestComponentGeneration")

    // leastGeneration is only used to ensure newer generation when the previous generation was invalidated due to an exception
    if (newestComponentGeneration < leastGeneration) {
      None
    } else if (restartOnRedeploy && ! componentSubscriber.internalRedeploy()) { // Don't reconfig - wait for restart
      None
    } else if (bootstrapSubscriber.generation < newestComponentGeneration) {
      val newestBootstrapGeneration = bootstrapSubscriber.waitNextGeneration()
      log.log(DEBUG, s"getConfigsOptional: new bootstrap generation: ${bootstrapSubscriber.generation}")
      bootstrapConfigIfChanged() orElse {
        if (newestBootstrapGeneration == newestComponentGeneration){
          log.log(DEBUG, s"Got new components configs with unchanged bootstrap configs.")
          componentsConfigIfChanged()
        } else {
          // This should not be a normal case, and hence a warning to allow investigation.
          log.warning(s"Did not get same generation for bootstrap ($newestBootstrapGeneration) and components configs ($newestComponentGeneration).")
          None
        }
      }
    } else {
      // bootstrapGen==componentGen (happens only when a new component subscriber returns first config after bootstrap)
      componentsConfigIfChanged()
    }
  }

  private def bootstrapConfigIfChanged():  Option[BootstrapConfigs]  = configIfChanged(bootstrapSubscriber, BootstrapConfigs)
  private def componentsConfigIfChanged(): Option[ComponentsConfigs] = configIfChanged(componentSubscriber, ComponentsConfigs)

  private def configIfChanged[T <: ConfigSnapshot](subscriber: Subscriber,
                                                   constructor: Map[ConfigKeyT, ConfigInstance] => T ): Option[T] = {
    if (subscriber.configChanged) Some(constructor(subscriber.config.asScala.toMap))
    else None
  }

  private def resetComponentSubscriberIfBootstrap(snapshot: ConfigSnapshot) {
    snapshot match {
      case BootstrapConfigs(_) => setupComponentSubscriber(Set())
      case _ =>
    }
  }

  private def setupComponentSubscriber(keys: Set[ConfigKeyT]) {
    if (componentSubscriberKeys != keys) {
      componentSubscriber.close()
      componentSubscriberKeys = keys
      try {
        log.log(DEBUG, s"Setting up new component subscriber for keys: $keys")
        componentSubscriber = subscribe(keys)
      } catch {
        case e: Throwable =>
          log.log(Level.WARNING, s"Failed setting up subscriptions for component configs: ${e.getMessage}")
          log.log(Level.WARNING, s"Config keys: $keys")
          throw e
      }
    }
  }

  def shutdown() {
    bootstrapSubscriber.close()
    componentSubscriber.close()
  }

  //TODO: check if these are really needed
  final def getBootstrapGeneration = bootstrapSubscriber.generation
  final def getComponentsGeneration = componentSubscriber.generation
}


object ConfigRetriever {
  private val log = Logger.getLogger(classOf[ConfigRetriever].getName)

  sealed abstract class ConfigSnapshot
  case class BootstrapConfigs(configs: Map[ConfigKeyT, ConfigInstance]) extends ConfigSnapshot
  case class ComponentsConfigs(configs: Map[ConfigKeyT, ConfigInstance]) extends ConfigSnapshot
}
