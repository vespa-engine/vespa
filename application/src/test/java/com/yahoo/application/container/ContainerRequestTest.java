// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container;

import com.yahoo.application.Networking;
import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Response;
import com.yahoo.application.container.handlers.DelayedThrowingInWriteRequestHandler;
import com.yahoo.application.container.handlers.DelayedWriteException;
import com.yahoo.application.container.handlers.EchoRequestHandler;
import com.yahoo.application.container.handlers.HeaderEchoRequestHandler;
import com.yahoo.application.container.handlers.ThrowingInWriteRequestHandler;
import com.yahoo.application.container.handlers.WriteException;
import com.yahoo.text.Utf8;
import org.junit.Test;

import java.nio.charset.CharacterCodingException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Einar M R Rosenvinge
 */
public class ContainerRequestTest {

    private static String getXML(String className, String binding) {
        return "<container version=\"1.0\">\n" +
               "  <handler id=\"test-handler\" class=\"" +
               className +
               "\">\n" +
               "    <binding>" +
               binding +
               "</binding>\n" +
               "  </handler>\n" +
               "  <accesslog type=\"disabled\" />" +
               "</container>";
    }

    @Test
    public void requireThatRequestBodyWorks() throws CharacterCodingException {
        String DATA = "we have no bananas today";
        Request req = new Request("http://banana/echo", DATA.getBytes(Utf8.getCharset()));

        try (JDisc container = JDisc.fromServicesXml(getXML(EchoRequestHandler.class.getCanonicalName(), "http://*/echo"), Networking.disable)) {
            Response response = container.handleRequest(req);
            assertThat(response.getBodyAsString(), equalTo(DATA));
            req.toString();
            response.toString();
        }
    }

    @Test
    public void requireThatCustomRequestHeadersWork() {
        Request req = new Request("http://banana/echo");
        req.getHeaders().add("X-Foo", "Bar");

        try (JDisc container = JDisc.fromServicesXml(getXML(HeaderEchoRequestHandler.class.getCanonicalName(), "http://*/echo"), Networking.disable)) {
            Response response = container.handleRequest(req);
            assertThat(response.getHeaders().contains("X-Foo", "Bar"), is(true));
            req.toString();
            response.toString();
        }
    }

    @Test(expected = WriteException.class)
    public void requireThatRequestHandlerThatThrowsInWriteWorks() {
        String DATA = "we have no bananas today";
        Request req = new Request("http://banana/throwwrite", DATA.getBytes(Utf8.getCharset()));

        try (JDisc container = JDisc.fromServicesXml(getXML(ThrowingInWriteRequestHandler.class.getCanonicalName(), "http://*/throwwrite"), Networking.disable)) {
            Response response = container.handleRequest(req);
            req.toString();
        }
    }

    @Test(expected = DelayedWriteException.class)
    public void requireThatRequestHandlerThatThrowsDelayedInWriteWorks() {
        String DATA = "we have no bananas today";
        Request req = new Request("http://banana/delayedthrowwrite", DATA.getBytes(Utf8.getCharset()));

        try (JDisc container = JDisc.fromServicesXml(getXML(DelayedThrowingInWriteRequestHandler.class.getCanonicalName(), "http://*/delayedthrowwrite"), Networking.disable)) {
            Response response = container.handleRequest(req);
            req.toString();
        }

    }

}
