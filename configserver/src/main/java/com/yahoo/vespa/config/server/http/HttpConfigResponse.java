// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.config.protocol.CompressionType;
import com.yahoo.vespa.config.protocol.ConfigResponse;

import java.io.IOException;
import java.io.OutputStream;

import static com.yahoo.jdisc.http.HttpResponse.Status.OK;

/**
 * HTTP getConfig response
 * 
 * @author lulf
 */
public class HttpConfigResponse extends HttpResponse {

    public static final String JSON_CONTENT_TYPE = "application/json";
    private final ConfigResponse config;

    private HttpConfigResponse(ConfigResponse config) {
        super(OK);
        this.config = config;
    }

    public static HttpConfigResponse createFromConfig(ConfigResponse config) {
        return new HttpConfigResponse(config);
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        config.serialize(outputStream, CompressionType.UNCOMPRESSED);
    }

    @Override
    public String getContentType() {
        return JSON_CONTENT_TYPE;
    }
    
}
