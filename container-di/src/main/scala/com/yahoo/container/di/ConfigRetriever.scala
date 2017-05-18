// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di


import config.Subscriber
import java.util.logging.Logger
import com.yahoo.log.LogLevel
import ConfigRetriever._
import annotation.tailrec
import com.yahoo.config.ConfigInstance
import scala.collection.JavaConversions._
import com.yahoo.vespa.config.ConfigKey

/**
 * @author tonytv
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
  final def getConfigs(componentConfigKeys: Set[ConfigKeyT], leastGeneration: Long): ConfigSnapshot = {
    require(componentConfigKeys intersect bootstrapKeys isEmpty)
    log.log(LogLevel.DEBUG, "getConfigs: " + componentConfigKeys)

    setupComponentSubscriber(componentConfigKeys ++ bootstrapKeys)

    getConfigsOptional(leastGeneration) match {
      case Some(snapshot) => resetComponentSubscriberIfBootstrap(snapshot); snapshot
      case None => getConfigs(componentConfigKeys, leastGeneration)
    }
  }

  private def getConfigsOptional(leastGeneration: Long): Option[ConfigSnapshot] = {
    val newestComponentGeneration = componentSubscriber.waitNextGeneration()

    if (newestComponentGeneration < leastGeneration) {
      None
    } else if (bootstrapSubscriber.generation < newestComponentGeneration) {
      val newestBootstrapGeneration = bootstrapSubscriber.waitNextGeneration()
      bootstrapConfigIfChanged() orElse {
        if (newestBootstrapGeneration == newestComponentGeneration) componentsConfigIfChanged()
        else None
      }
    } else {
      componentsConfigIfChanged()
    }
  }

  private def bootstrapConfigIfChanged():  Option[BootstrapConfigs]  = configIfChanged(bootstrapSubscriber, BootstrapConfigs)
  private def componentsConfigIfChanged(): Option[ComponentsConfigs] = configIfChanged(componentSubscriber, ComponentsConfigs)

  private def configIfChanged[T <: ConfigSnapshot](subscriber: Subscriber,
                                                   constructor: Map[ConfigKeyT, ConfigInstance] => T ):
  Option[T] = {
    if (subscriber.configChanged) Some(constructor(subscriber.config.toMap))
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
        componentSubscriber = subscribe(keys)
      } catch {
        case e: Throwable =>
          log.warning(s"Failed setting up subscriptions for component configs: ${e.getMessage} - Config keys: $keys")
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
