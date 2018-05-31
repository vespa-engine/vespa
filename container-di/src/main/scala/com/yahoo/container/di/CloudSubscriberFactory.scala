// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di

import java.util.logging.{Level, Logger}

import com.yahoo.config.ConfigInstance
import com.yahoo.config.subscription.{ConfigHandle, ConfigSource, ConfigSourceSet, ConfigSubscriber}
import com.yahoo.container.di.CloudSubscriberFactory._
import com.yahoo.container.di.config.{Subscriber, SubscriberFactory}
import com.yahoo.vespa.config.ConfigKey

import scala.collection.JavaConverters._
import scala.language.existentials


/**
 * @author Tony Vaagenes
 */

class CloudSubscriberFactory(configSource: ConfigSource) extends SubscriberFactory
{
  private var testGeneration: Option[Long] = None

  private val activeSubscribers = new java.util.WeakHashMap[CloudSubscriber, Int]()

  override def getSubscriber(configKeys: java.util.Set[_ <: ConfigKey[_]]): Subscriber = {
    val subscriber = new CloudSubscriber(configKeys.asScala.toSet.asInstanceOf[Set[ConfigKeyT]], configSource)

    testGeneration.foreach(subscriber.subscriber.reload(_)) //TODO: test specific code, remove
    activeSubscribers.put(subscriber, 0)

    subscriber
  }

  //TODO: test specific code, remove
  override def reloadActiveSubscribers(generation: Long) {
    testGeneration = Some(generation)

    val l = activeSubscribers.keySet().asScala.toSet
    l.foreach { _.subscriber.reload(generation) }
  }
}

object CloudSubscriberFactory {
  val log = Logger.getLogger(classOf[CloudSubscriberFactory].getName)

  private class CloudSubscriber(keys: Set[ConfigKeyT], configSource: ConfigSource) extends Subscriber
  {
    private[CloudSubscriberFactory] val subscriber = new ConfigSubscriber(configSource)
    private val handles: Map[ConfigKeyT, ConfigHandle[_ <: ConfigInstance]] = keys.map(subscribe).toMap


    // if waitNextGeneration has not yet been called, -1 should be returned
    var generation: Long = -1

    // True if this reconfiguration was caused by a system-internal redeploy, not an external application change
    var internalRedeploy: Boolean = false

    private def subscribe(key: ConfigKeyT) = (key, subscriber.subscribe(key.getConfigClass, key.getConfigId))

    override def configChanged = handles.values.exists(_.isChanged)

    //mapValues returns a view,, so we need to force evaluation of it here to prevent deferred evaluation.
    override def config = handles.mapValues(_.getConfig).toMap.view.force.
      asInstanceOf[Map[ConfigKey[ConfigInstance], ConfigInstance]].asJava

    override def waitNextGeneration() = {
      require(!handles.isEmpty)

      /* Catch and just log config exceptions due to missing config values for parameters that do
       * not have a default value. These exceptions occur when the user has removed a component
       * from services.xml, and the component takes a config that has parameters without a
       * default value in the def-file. There is a new 'components' config underway, where the
       * component is removed, so this old config generation will soon be replaced by a new one. */
      var gotNextGen = false
      var numExceptions = 0
      while (!gotNextGen) {
        try{
          if (subscriber.nextGeneration())
            gotNextGen = true
        } catch {
          case e: IllegalArgumentException =>
            numExceptions += 1
            log.log(Level.WARNING, "Got exception from the config system (please ignore the exception if you just removed "
                          + "a component from your application that used the mentioned config): ", e)
            if (numExceptions >= 5)
              throw new IllegalArgumentException("Failed retrieving the next config generation.", e)
        }
      }

      generation = subscriber.getGeneration
      internalRedeploy = subscriber.isInternalRedeploy
      generation
    }

    override def close() {
      subscriber.close()
    }
  }


  class Provider extends com.google.inject.Provider[SubscriberFactory] {
    override def get() =  new CloudSubscriberFactory(ConfigSourceSet.createDefault())
  }
}
