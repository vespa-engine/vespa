// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.standalone

import com.yahoo.config.{ConfigBuilder, ConfigInstance}
import com.yahoo.container.di.ConfigKeyT
import com.yahoo.container.di.config.{Subscriber, SubscriberFactory}
import com.yahoo.container.standalone.StandaloneSubscriberFactory._
import com.yahoo.vespa.config.ConfigKey
import com.yahoo.vespa.model.VespaModel

import scala.collection.JavaConverters._

/**
 * @author tonytv
 * @author gjoranv
 */
class StandaloneSubscriberFactory(root: VespaModel) extends SubscriberFactory {
  class StandaloneSubscriber(configKeys: Set[ConfigKeyT]) extends Subscriber {
    override def configChanged =
      generation == 0

    override def close() {}

    override def config = {

      def getConfig(key: ConfigKeyT) = {
        val builderWithModelConfig = root.getConfig(newBuilderInstance(key), key.getConfigId)

        require(builderWithModelConfig != null, "Invalid config id " + key.getConfigId )
        (key.asInstanceOf[ConfigKey[ConfigInstance]], newConfigInstance(builderWithModelConfig))
      }

      (configKeys map getConfig).toMap.asJava
    }

    override def waitNextGeneration() = {
      generation += 1

      if (generation != 0) {
        while (!Thread.interrupted())
          Thread.sleep(10000)
      }

      generation
    }

    //if waitNextGeneration has not yet been called, -1 should be returned
    var generation = -1L
  }

  override def getSubscriber(configKeys: java.util.Set[_ <: ConfigKey[_]]) =
    new StandaloneSubscriber(configKeys.asScala.toSet.asInstanceOf[Set[ConfigKeyT]])

  def reloadActiveSubscribers(generation: Long) {
    throw new RuntimeException("unsupported")
  }
}

object StandaloneSubscriberFactory {

  private def newBuilderInstance(key: ConfigKeyT) =
    builderClass(key).getDeclaredConstructor().newInstance()

  private def builderClass(key: ConfigKeyT) = {
    val nestedClasses = key.getConfigClass.getClasses
    nestedClasses.
      filter {_.getName.equals(key.getConfigClass.getName + "$Builder")}.
      head.
      asInstanceOf[Class[ConfigInstance.Builder]]
  }

  private def newConfigInstance(builder: ConfigBuilder) =
    configClass(builder).getConstructor(builder.getClass).newInstance(builder)

  private def configClass(builder: ConfigBuilder) =
    builder.getClass.getEnclosingClass.asInstanceOf[Class[ConfigInstance]]

}
