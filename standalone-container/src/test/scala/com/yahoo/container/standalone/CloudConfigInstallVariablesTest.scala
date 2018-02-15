// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.standalone

import org.junit.Test
import org.junit.Assert.assertThat
import org.hamcrest.CoreMatchers.is
import org.hamcrest.Matchers.{arrayContaining}

/**
 * @author lulf
 * @author tonytv
 * @since 5.
 */
class CloudConfigInstallVariablesTest {
  def convert = CloudConfigInstallVariables.configServerConverter.convert _

  @Test
  def test_configserver_parsing {
    val parsed = convert("myhost.mydomain.com")
    assertThat(parsed.length, is(1))
  }

  @Test
  def port_can_be_configured {
    val parsed = convert("myhost:123")
    val port: Int = parsed(0).port.get()
    assertThat(port, is(123))
  }

  @Test
  def multiple_spaces_are_supported {
    val parsed = convert("test1     test2")
    assertThat(parsed.size, is(2))

    val hostNames = parsed.map(_.hostName)
    assertThat(hostNames, arrayContaining("test1", "test2"))
  }

  @Test(expected = classOf[IllegalArgumentException])
  def missing_port_gives_exception {
    convert("myhost:")
  }

  @Test(expected = classOf[IllegalArgumentException])
  def non_numeric_port_gives_exception {
    convert("myhost:non-numeric")
  }

  @Test
  def string_arrays_are_split_on_spaces {
    val parsed = convert("/home/vespa/foo /home/vespa/bar ")
    assertThat(parsed.size, is(2))
  }

  @Test
  def string_arrays_are_split_on_comma {
    val parsed = convert("/home/vespa/foo,/home/vespa/bar,")
    assertThat(parsed.size, is(2))
  }
}
