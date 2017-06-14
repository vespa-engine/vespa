// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di

import org.junit.Assert._
import org.hamcrest.CoreMatchers.{is, instanceOf => hamcrestInstanceOf}
import com.yahoo.vespa.config.ConfigKey
import com.yahoo.config.test.{TestConfig, Bootstrap2Config, Bootstrap1Config}
import com.yahoo.container.di.ConfigRetriever.{ComponentsConfigs, BootstrapConfigs}
import org.junit.{Ignore, After, Before, Test}
import scala.collection.JavaConversions._
import scala.reflect.ClassTag
import org.hamcrest.Matcher

/**
 *
 * @author gjoranv
 * @author tonytv
 */
class ConfigRetrieverTest {
  var dirConfigSource: DirConfigSource = null

  @Before def setup()  {
    dirConfigSource = new DirConfigSource("ConfigRetrieverTest-")
  }
  @After def cleanup() { dirConfigSource.cleanup() }

  @Test
  def require_that_bootstrap_configs_come_first() {
    writeConfigs()
    val retriever = createConfigRetriever()
    val bootstrapConfigs = retriever.getConfigs(Set(), 0)

    assertThat(bootstrapConfigs, instanceOf[BootstrapConfigs])
  }

  @Test
  def require_that_components_comes_after_bootstrap() {
    writeConfigs()
    val retriever = createConfigRetriever()
    val bootstrapConfigs = retriever.getConfigs(Set(), 0)

    val testConfigKey = new ConfigKey(classOf[TestConfig], dirConfigSource.configId)
    val componentsConfigs = retriever.getConfigs(Set(testConfigKey), 0)

    componentsConfigs match {
      case ComponentsConfigs(configs) => assertThat(configs.size, is(3))
      case _ => fail("ComponentsConfigs has unexpected type: " + componentsConfigs)
    }
  }

  @Test(expected = classOf[IllegalArgumentException])
  @Ignore
  def require_exception_upon_modified_components_keys_without_bootstrap() {
    writeConfigs()
    val retriever = createConfigRetriever()
    val testConfigKey = new ConfigKey(classOf[TestConfig], dirConfigSource.configId)
    val bootstrapConfigs = retriever.getConfigs(Set(), 0)
    val componentsConfigs = retriever.getConfigs(Set(testConfigKey), 0)
    retriever.getConfigs(Set(testConfigKey, new ConfigKey(classOf[TestConfig],"")), 0)
  }

  @Test
  def require_that_empty_components_keys_after_bootstrap_returns_components_configs() {
    writeConfigs()
    val retriever = createConfigRetriever()
    assertThat(retriever.getConfigs(Set(), 0), instanceOf[BootstrapConfigs])
    assertThat(retriever.getConfigs(Set(), 0), instanceOf[ComponentsConfigs])
  }

  def writeConfigs() {
    writeConfig("bootstrap1", """dummy "ignored" """")
    writeConfig("bootstrap2", """dummy "ignored" """")
    writeConfig("test", """stringVal "ignored" """")
  }

  def createConfigRetriever() = {
    val configId = dirConfigSource.configId
    val subscriber = new CloudSubscriberFactory(dirConfigSource.configSource)
    new ConfigRetriever(
      Set(new ConfigKey(classOf[Bootstrap1Config], configId),
          new ConfigKey(classOf[Bootstrap2Config], configId)),
      subscriber.getSubscriber(_))
  }

  def writeConfig = dirConfigSource.writeConfig _

  def instanceOf[T: ClassTag] = hamcrestInstanceOf(implicitly[ClassTag[T]].runtimeClass): Matcher[AnyRef]
}
