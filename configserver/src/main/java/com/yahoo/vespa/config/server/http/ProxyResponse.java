// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.container.jdisc.HttpResponse;
import org.apache.http.Header;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;

/**
 * Proxies response back to client, keeps Content-Type header if it is present
 *
 * @author olaa
 * @author freva
 */
class ProxyResponse extends HttpResponse {

    private final org.apache.http.HttpResponse clientResponse;

    ProxyResponse(org.apache.http.HttpResponse clientResponse) {
        super(clientResponse.getStatusLine().getStatusCode());
        this.clientResponse = clientResponse;
    }

    @Override
    public String getContentType() {
        return Optional.ofNullable(clientResponse.getFirstHeader("Content-Type"))
                .map(Header::getValue)
                .orElseGet(super::getContentType);
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        clientResponse.getEntity().writeTo(outputStream);
    }
}
