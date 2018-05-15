// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.standalone

import com.yahoo.container.standalone.CloudConfigInstallVariables.{toConfigModelsPluginDir, toConfigServer, toConfigServers}
import org.hamcrest.CoreMatchers.is
import org.hamcrest.Matchers.arrayContaining
import org.junit.Assert.assertThat
import org.junit.Test

/**
 * @author Ulf Lilleengen
 * @author Tony Vaagenes
 */
class CloudConfigInstallVariablesTest {

  @Test
  def test_configserver_parsing {
    val parsed = toConfigServers("myhost.mydomain.com")
    assertThat(parsed.length, is(1))
  }

  @Test
  def port_can_be_configured {
    val parsed = toConfigServers("myhost:123")
    val port: Int = parsed(0).port.get()
    assertThat(port, is(123))
  }

  @Test
  def multiple_spaces_are_supported {
    val parsed = toConfigServers("test1     test2")
    assertThat(parsed.size, is(2))

    val hostNames = parsed.map(_.hostName)
    assertThat(hostNames, arrayContaining("test1", "test2"))
  }

  @Test(expected = classOf[IllegalArgumentException])
  def missing_port_gives_exception {
    toConfigServer("myhost:")
  }

  @Test(expected = classOf[IllegalArgumentException])
  def non_numeric_port_gives_exception {
    toConfigServer("myhost:non-numeric")
  }

  @Test
  def string_arrays_are_split_on_spaces {
    val parsed = toConfigModelsPluginDir("/home/vespa/foo /home/vespa/bar ")
    assertThat(parsed.size, is(2))
  }

  @Test
  def string_arrays_are_split_on_comma {
    val parsed = toConfigModelsPluginDir("/home/vespa/foo,/home/vespa/bar,")
    assertThat(parsed.size, is(2))
  }
}
