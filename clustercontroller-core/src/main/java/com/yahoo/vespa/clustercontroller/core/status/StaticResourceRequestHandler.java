// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.status;

import com.yahoo.vespa.clustercontroller.core.status.statuspage.StatusPageResponse;
import com.yahoo.vespa.clustercontroller.core.status.statuspage.StatusPageServer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;

/**
 * HTTP request handler for serving a single JAR resource as if it were
 * a regular file hosted on the server. Always serves the content verbatim
 * (i.e. as a byte stream), specifying a Content-Type provided when creating
 * the handler.
 *
 * @author <a href="mailto:vekterli@yahoo-inc.com">Tor Brede Vekterli</a>
 * @since 5.28
 */
public class StaticResourceRequestHandler implements StatusPageServer.RequestHandler {
    private final byte[] resourceData;
    private final String contentType;

    public StaticResourceRequestHandler(String resourcePath,
                                        String contentType)
            throws IOException
    {
        this.resourceData = loadResource(resourcePath);
        this.contentType = contentType;
    }

    private byte[] loadResource(String resourcePath) throws IOException {
        InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (resourceStream == null) {
            throw new IOException("No resource with path '" + resourcePath + "' could be found");
        }
        return readStreamData(resourceStream);
    }

    @Override
    public StatusPageResponse handle(StatusPageServer.HttpRequest request) {
        final StatusPageResponse response = new StatusPageResponse();
        response.setClientCachingEnabled(true);
        response.setContentType(contentType);
        try {
            response.getOutputStream().write(resourceData);
        } catch (IOException e) {
            response.setResponseCode(StatusPageResponse.ResponseCode.INTERNAL_SERVER_ERROR);
        }
        return response;
    }

    private byte[] readStreamData(InputStream resourceStream) throws IOException {
        final byte[] buf = new byte[4096];
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        while (true) {
            int read = resourceStream.read(buf);
            if (read < 0) {
                break;
            }
            outputStream.write(buf, 0, read);
        }
        outputStream.close();
        return outputStream.toByteArray();
    }
}
