// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler.observability

import java.util.concurrent.Executor

import HtmlUtil._
import OverviewHandler._
import com.yahoo.container.jdisc.{HttpResponse, HttpRequest, ThreadedHttpRequestHandler}
import com.yahoo.text.Utf8
import java.io.{PrintWriter, OutputStream}


/**
 * @author gjoranv
 * @author tonytv
 */
class OverviewHandler(executor: Executor) extends ThreadedHttpRequestHandler(executor) {

  @volatile
  private var dotGraph: String = _

  def handle(request: HttpRequest): HttpResponse = {
    val path = request.getUri.getPath

    try {
      if (path.endsWith("/ComponentGraph"))
        handleComponentGraph(request)
      else if (path.endsWith("/Overview"))
        handleOverview(request)
      else
        null
    } catch {
      case e: Exception => errorResponse(e.getMessage)
    }

  }

  def handleOverview(request: HttpRequest): HttpResponse = {
    new HttpResponse(com.yahoo.jdisc.Response.Status.OK) {
      def render(stream: OutputStream) {
        stream.write(Utf8.toBytes(overviewPageText))
      }

      override def getContentType: String = {
        "text/html"
      }
    }
  }

  def errorResponse(message: String): HttpResponse = {
    new HttpResponse(com.yahoo.jdisc.Response.Status.BAD_REQUEST) {
      def render(stream: OutputStream) {
        new PrintWriter(stream).println(message)
      }
    }
  }

  def handleComponentGraph(request: HttpRequest): HttpResponse = {
    var graphType = request.getProperty("type");
    if (graphType == null)
      graphType = "text"

    graphType match {
      case "text" => textualComponentGraph(dotGraph)
      case t if componentGraphTypes.contains(t) => graphicalComponentGraph(t, Graphviz.runDot(graphType,dotGraph))
      case t => errorResponse(t)
    }
  }

  def textualComponentGraph(dotGraph: String) =
    new HttpResponse(com.yahoo.jdisc.Response.Status.OK) {
      def render(stream: OutputStream) {
        stream.write(Utf8.toBytes(dotGraph))
      }

      override def getContentType: String = {
        "text/plain"
      }
    }

  def graphicalComponentGraph(graphType: String, image: Array[Byte] ): HttpResponse =
    new HttpResponse(com.yahoo.jdisc.Response.Status.OK) {
      def render(output: OutputStream) {
        output.write(image)
      }

      override def getContentType: String = {
        componentGraphTypes(graphType)
      }
    }

  def setDotGraph(dotGraph: String) {
    this.dotGraph = dotGraph
  }
}

object OverviewHandler {
  val componentGraphTypes = Map(
    "svg"  -> "image/svg+xml",
    "png"  -> "image/png",
    "text" -> "text/plain")

  val overviewPageText = prettyPrintXhtml(overviewPage)

  private def overviewPage = {
    def componentGraphLink(graphType: String) = link("Overview/ComponentGraph?type=" + graphType, graphType)


    html(
      title = "Container Overview",
      body =
        h1("Container Overview"),
        unorderedList(
          li(link("ApplicationStatus")),
          li("ComponentGraph" +: (componentGraphTypes.keys map {componentGraphLink}).toSeq :_*)))
  }

}
