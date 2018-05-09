// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.standalone

import org.junit.{Ignore, Test}
import org.junit.Assert.assertThat
import org.hamcrest.CoreMatchers.is
import org.hamcrest.number.OrderingComparison.greaterThan

import StandaloneContainer.withContainerModel
import com.yahoo.vespa.config.ConfigKey
import com.yahoo.config.ConfigInstance
import com.yahoo.container.{ComponentsConfig, BundlesConfig, di}
import scala.collection.JavaConverters._

/**
 * @author tonytv
 */
class StandaloneSubscriberTest {
  val bundlesKey = key("bundles")
  val componentsKey = key("components")

  def key(name: String) = new ConfigKey(name, "container", "container").asInstanceOf[ConfigKey[ConfigInstance]]

  def box(i: Int) = java.lang.Integer.valueOf(i)

  @Test
  @Ignore
  def standalone_subscriber() {
    withContainerModel(<container version="1.0"> </container>) { root =>
      val subscriber = new StandaloneSubscriberFactory(root).getSubscriber(Set(bundlesKey, componentsKey).asJava)
      val config = subscriber.config.asScala
      assertThat(config.size, is(2))

      val bundlesConfig = config(bundlesKey).asInstanceOf[BundlesConfig]
      val componentsConfig = config(componentsKey).asInstanceOf[ComponentsConfig]

      assertThat(bundlesConfig.bundle().size(), is(0))
      assertThat(box(componentsConfig.components().size()), greaterThan(box(10)))
    }
  }
}
