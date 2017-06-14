// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container

import org.junit.Test
import searchers.AddHitSearcher
import com.yahoo.component.ComponentSpecification
import com.yahoo.search.Query
import org.junit.Assert._
import org.hamcrest.CoreMatchers._
import org.hamcrest.Matchers.containsString;
import JDiscTest.{fromServicesXml, using}


/**
 *
 * @author gjoranv
 * @since 5.1.15
 */
class JDiscContainerSearchTest {

  @Test
  def processing_and_rendering_works() {
    val searcherId = classOf[AddHitSearcher].getName

    using(containerWithSearch(searcherId))
    { container =>
      val rendered = container.search.processAndRender(ComponentSpecification.fromString("mychain"),
        ComponentSpecification.fromString("DefaultRenderer"), new Query(""))
      val renderedAsString = new String(rendered, "utf-8")
      assertThat(renderedAsString, containsString(searcherId))
    }
  }

  @Test
  def searching_works() {
    val searcherId = classOf[AddHitSearcher].getName

    using(containerWithSearch(searcherId))
    { container =>
      val searching = container.search
      val result = searching.process(ComponentSpecification.fromString("mychain"), new Query(""))

      val hitTitle = result.hits().get(0).getField("title").asInstanceOf[String]
      assertThat(hitTitle, is(searcherId))
    }
  }

  def containerWithSearch(searcherId: String) = {
    fromServicesXml(
      <container version="1.0">
        <search>
          <chain id="mychain">
            <searcher id={searcherId}/>
          </chain>
        </search>
      </container>)
  }

  @Test(expected = classOf[UnsupportedOperationException])
  def retrieving_search_from_container_without_search_is_illegal() {
    using(JDiscTest.fromServicesXml(
        <container version="1.0" />
    ))
    { container =>
      container.search  // throws
    }
  }

}
