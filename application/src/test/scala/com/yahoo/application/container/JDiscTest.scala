// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container

import com.yahoo.container.Container
import com.yahoo.jdisc.http.server.jetty.JettyHttpServer

import scala.language.implicitConversions
import handler.Request
import org.junit.{Ignore, Test}
import org.junit.Assert.{assertThat, assertNotNull, fail}
import org.hamcrest.CoreMatchers.{is, containsString, hasItem}
import java.nio.file.FileSystems
import com.yahoo.search.Query
import com.yahoo.component.ComponentSpecification
import handlers.TestHandler
import xml.{Node, Elem}
import JDiscTest._
import com.yahoo.application.{Networking, ApplicationBuilder}

import scala.collection.convert.wrapAsScala._


/**
 * @author tonytv
 * @author gjoranv
 */
class JDiscTest {
  @Test
  def jdisc_can_be_used_as_top_level_element() {
    using(fromServicesXml(
      <jdisc version="1.0">
        <search />
      </jdisc>))
    { container =>
      assertNotNull(container.search())
    }
  }

  @Test
  def jdisc_id_can_be_set() {
    using(fromServicesXml(
      <jdisc version="1.0" id="my-service-id">
        <search />
      </jdisc>))
    { container =>
      assertNotNull(container.search())
    }
  }

  @Test
  def jdisc_can_be_embedded_in_services_tag() {
    using(fromServicesXml(
      <services>
        <jdisc version="1.0" id="my-service-id">
          <search />
        </jdisc>
      </services>))
    { container =>
      assertNotNull(container.search())
    }
  }

  @Test
  def multiple_jdisc_elements_gives_exception() {
    try {
      using(fromServicesXml(
        <services>
          <jdisc version="1.0" id="id1" />
          <jdisc version="1.0" />
          <container version="1.0"/>
        </services>))
      { container => fail("expected exception")}
    } catch {
      case e: Exception => assertThat(e.getMessage, containsString("container id='', jdisc id='id1', jdisc id=''"))
    }
  }

  @Test
  def handleRequest_yields_response_from_correct_request_handler() {
    using(fromServicesXml(
        <container version="1.0">
          <handler id="test-handler" class={classOf[TestHandler].getName}>
            <binding>http://*/TestHandler</binding>
          </handler>
        </container>))
    { container =>
      val response = container.handleRequest(new Request("http://foo/TestHandler"))
      assertThat(response.getBodyAsString, is(TestHandler.RESPONSE))
    }
  }

  @Test
  def load_searcher_from_bundle() {
    using(JDisc.fromPath(FileSystems.getDefault.getPath("src/test/app-packages/searcher-app"), Networking.disable))
    { container =>
      val result = container.search.process(ComponentSpecification.fromString("default"),new Query("?query=ignored"))
      assertThat(result.hits().get(0).getField("title").toString, is("Heal the World!"))
    }
  }

  @Test
  def document_types_can_be_accessed() {
    using(new ApplicationBuilder().documentType("example", exampleDocument).
      servicesXml(containerWithDocumentProcessing).
      build())
    { application =>
      val container = application.getJDisc("jdisc")
      val processing = container.documentProcessing()
      assertThat(processing.getDocumentTypes.keySet(), hasItem("example"))
    }
  }

  @Test
  def annotation_types_can_be_accessed() {
    using(new ApplicationBuilder().documentType("example",
      s"""
          |search example {
          |  ${exampleDocument}
          |  annotation exampleAnnotation {}
          |}
          """.stripMargin).
      servicesXml(containerWithDocumentProcessing).
      build())
    { application =>
      val container = application.getJDisc("jdisc")
      val processing = container.documentProcessing()
      assertThat(processing.getAnnotationTypes.keySet(), hasItem("exampleAnnotation"))
    }
  }

  @Ignore //Enable this when static state has been removed.
  @Test
  def multiple_containers_can_be_run_in_parallel() {
    def sendRequest(jdisc: JDisc) {
      val response = jdisc.handleRequest(new Request("http://foo/TestHandler"))
      assertThat(response.getBodyAsString, is(TestHandler.RESPONSE))
    }

    using(jdiscWithHttp()) { jdisc1 =>
      using(jdiscWithHttp()) { jdisc2 =>
        sendRequest(jdisc1)
        sendRequest(jdisc2)
      }
    }
  }
}

object JDiscTest {

  def fromServicesXml(elem: Elem, networking: Networking = Networking.disable) =
    JDisc.fromServicesXml(elem.toString(), networking)

  def using[T <: AutoCloseable, U](t: T)(f: T => U ) = {
    try {
      f(t)
    } finally {
      t.close()
    }
  }

  implicit def xmlToString(xml: Node): String = xml.toString()

  val containerWithDocumentProcessing =
    <jdisc version="1.0">
      <http />
      <document-processing />
    </jdisc>

  val exampleDocument =
    """
      |document example {
      |
      |  field title type string {
      |    indexing: summary | index   # How this field should be indexed
      |    weight: 75 # Ranking importancy of this field, used by the built in nativeRank feature
      |    header
      |  }
      |}
    """.stripMargin


  def jdiscWithHttp() = {
    fromServicesXml(
      <jdisc version="1.0">
        <handler id={classOf[TestHandler].getName} />
        <http>
          <server id="main" port="9999" />
        </http>
      </jdisc>)
  }

  def getListenPort: Int =
    Container.get.getServerProviderRegistry.allComponents().collectFirst {
      case server: JettyHttpServer => server.getListenPort
    } getOrElse {
      throw new RuntimeException("No http server found")
    }
}
