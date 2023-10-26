// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import org.eclipse.jetty.util.ByteArrayISO8859Writer;
import org.eclipse.jetty.util.StringUtil;

import java.io.IOException;

/**
 * Creates HTML body having the status code, error message and request uri.
 * The body is constructed from a template that is inspired by the default Jetty template (see {@link org.eclipse.jetty.server.Response#sendError(int, String)}).
 * The content is written using the ISO-8859-1 charset.
 *
 * @author bjorncs
 */
class ErrorResponseContentCreator {

    private final ByteArrayISO8859Writer writer = new ByteArrayISO8859Writer(2048);

    byte[] createErrorContent(String requestUri, int statusCode, String message) {
        String sanitizedString = message != null ? StringUtil.sanitizeXmlString(message) : "";
        String statusCodeString = Integer.toString(statusCode);
        writer.resetWriter();
        try {
            writer.write("<html>\n<head>\n<meta http-equiv=\"Content-Type\" content=\"text/html;charset=ISO-8859-1\"/>\n<title>Error ");
            writer.write(statusCodeString);
            writer.write("</title>\n</head>\n<body>\n<h2>HTTP ERROR: ");
            writer.write(statusCodeString);
            writer.write("</h2>\n<p>Problem accessing ");
            writer.write(StringUtil.sanitizeXmlString(requestUri));
            writer.write(". Reason:\n<pre>    ");
            writer.write(sanitizedString);
            writer.write("</pre></p>\n<hr/>\n</body>\n</html>\n");
        } catch (IOException e) {
            // IOException should not be thrown unless writer is constructed using byte[] parameter
            throw new RuntimeException(e);
        }
        return writer.getByteArray();
    }
}
