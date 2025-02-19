// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;


import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @author bjorncs
 */
public class ErrorResponseContentCreatorTest {

    @Test
    void response_content_matches_expected_string() {
        String expectedHtml =
                "<html>\n" +
                        "<head>\n" +
                        "<meta http-equiv=\"Content-Type\" content=\"text/html;charset=ISO-8859-1\"/>\n" +
                        "<title>Error 200</title>\n" +
                        "</head>\n" +
                        "<body>\n" +
                        "<h2>HTTP ERROR: 200</h2>\n" +
                        "<p>Problem accessing http://foo.bar. Reason:\n" +
                        "<pre>    My custom error message</pre></p>\n" +
                        "<hr/>\n" +
                        "</body>\n" +
                        "</html>\n";

        ErrorResponseContentCreator c = new ErrorResponseContentCreator();
        byte[] rawContent = c.createErrorContent(
                "http://foo.bar",
                HttpServletResponse.SC_OK,
                "My custom error message");
        String actualHtml = new String(rawContent, StandardCharsets.ISO_8859_1);
        assertEquals(expectedHtml, actualHtml);
    }

}
