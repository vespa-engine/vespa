// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container.handlers

import com.yahoo.jdisc.handler.{ResponseDispatch, ResponseHandler, AbstractRequestHandler}
import TestHandler._

/**
 * @author gjoranv
 * @since 5.1.15
 */

class TestHandler extends AbstractRequestHandler {
  def handleRequest(request:JDiscRequest, handler: ResponseHandler) = {
    val writer = ResponseDispatch.newInstance(com.yahoo.jdisc.Response.Status.OK).connectFastWriter(handler)
    writer.write(RESPONSE)
    writer.close()
    null
  }
}
object TestHandler {
  val RESPONSE = "Hello, World!"
  type JDiscRequest = com.yahoo.jdisc.Request
}
