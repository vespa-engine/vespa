package com.yahoo.application.container;

import com.yahoo.application.Networking;
import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Response;
import com.yahoo.text.Utf8;
import org.junit.Test;

import java.nio.charset.CharacterCodingException;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class HttpFilterTest {

    private static String getXML() {
        return "<container version='1.0'>" +
               "  <handler id='test-handler' class='com.yahoo.application.container.handlers.EchoRequestHandler'>" +
               "    <binding>http://*/*</binding>" +
               "  </handler>" +
               "  <http>" +
               "    <server port='4080' id='controller4080'/>"+
               "    <filtering>" +
               "      <request-chain id='request-filters'>" +
               "        <filter id='com.yahoo.application.container.filters.ThrowingFilter'/>" +
               "        <binding>http://*/*</binding>" +
               "      </request-chain>" +
               "    </filtering>"+
               "  </http>" +
               "</container>";
    }

    @Test
    public void testFilterInvocation() throws InterruptedException, CharacterCodingException {
        String requestData = "data";
        Request req = new Request("http://localhost/echo", requestData.getBytes(Utf8.getCharset()));

        try (JDisc container = JDisc.fromServicesXml(getXML(), Networking.disable)) {
            Response response = container.handleRequest(req);
            assertEquals(response.getBodyAsString(), requestData);
            req.toString();
            response.toString();
        }
    }

}
