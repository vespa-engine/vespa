// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application

import container.JDiscTest._
import java.nio.file.Files
import org.junit.Assert.{assertTrue, assertThat, fail}
import org.junit.Test
import com.yahoo.io.IOUtils
import org.hamcrest.CoreMatchers.containsString

/**
 * @author tonytv
 */
 class ApplicationBuilderTest {
    @Test
    def query_profile_types_can_be_added() {
        withApplicationBuilder { builder =>
            builder.queryProfileType("MyProfileType",
              <query-profile-type id="MyProfileType">
                <field name="age" type="integer" />
                <field name="profession" type="string" />
                <field name="user" type="query-profile:MyUserProfile" />
              </query-profile-type>)

          assertTrue(Files.exists(builder.getPath.resolve("search/query-profiles/types/MyProfileType.xml")))
        }
    }


  @Test
  def query_profile_can_be_added() {
    withApplicationBuilder { builder =>
      builder.queryProfile("MyProfile",
        <query-profile id="MyProfile">
          <field name="message">Hello world!</field>
        </query-profile>)

      assertTrue(Files.exists(builder.getPath.resolve("search/query-profiles/MyProfile.xml")))
    }
  }

  @Test
  def rank_expression_can_be_added() {
     withApplicationBuilder { builder =>
       builder.rankExpression("myExpression", "content")
       assertTrue(Files.exists(builder.getPath.resolve("searchdefinitions/myExpression.expression")))
    }
  }

  @Test
  def builder_cannot_be_reused() {
    val builder = new ApplicationBuilder
    builder.servicesXml(<jdisc version="1.0" />)

    using(builder.build()) { builder => }

    try {
      builder.servicesXml("")
      fail("Expected exception.")
    } catch {
      case e: RuntimeException => assertThat(e.getMessage, containsString("build method"))
    }

  }

  def withApplicationBuilder(f: ApplicationBuilder => Unit) {
    val builder = new ApplicationBuilder()
    try {
      f(builder)
    } finally {
      IOUtils.recursiveDeleteDir(builder.getPath.toFile)
    }
  }
}
