package com.yahoo.application.container.handlers;

import com.yahoo.jdisc.handler.*;

public class TestHandler extends AbstractRequestHandler {
	public static final String RESPONSE = "Hello, World!";

	public ContentChannel handleRequest(com.yahoo.jdisc.Request request, ResponseHandler handler) {
		FastContentWriter writer = ResponseDispatch.newInstance(com.yahoo.jdisc.Response.Status.OK)
				.connectFastWriter(handler);
		writer.write(RESPONSE);
		writer.close();
		return null;
	}

}
