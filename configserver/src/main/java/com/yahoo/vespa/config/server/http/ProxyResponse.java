// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.container.jdisc.HttpResponse;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.NameValuePair;

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

    private final CloseableHttpResponse clientResponse;

    ProxyResponse(CloseableHttpResponse clientResponse) {
        super(clientResponse.getCode());
        this.clientResponse = clientResponse;
    }

    @Override
    public String getContentType() {
        return Optional.ofNullable(clientResponse.getFirstHeader("Content-Type"))
                .map(NameValuePair::getValue)
                .orElseGet(super::getContentType);
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        try (clientResponse) {
            clientResponse.getEntity().writeTo(outputStream);
        }
    }

    @Override
    public long maxPendingBytes() {
        return 1 << 25; // 32MB
    }

}
