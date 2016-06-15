// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.client;

import com.ning.http.client.RequestBuilder;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.core.HeaderFieldsUtil;

import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
final class RequestBuilderFactory {

    private RequestBuilderFactory() {
        // hide
    }

    public static RequestBuilder newInstance(Request request, HttpRequest.Method method) {
        RequestBuilder builder = new RequestBuilder();
        if (request instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest)request;
            builder.setProxyServer(ProxyServerFactory.newInstance(httpRequest.getProxyServer()));

            Long timeout = httpRequest.getConnectionTimeout(TimeUnit.MILLISECONDS);
            if (timeout != null) {
                // TODO: Uncomment the next line once ticket 5536510 has been resolved.
                // builder.setConnectTimeout(timeout);
            }
        }
        builder.setMethod(method.name());
        builder.setUrl(request.getUri().toString());
        HeaderFieldsUtil.copyHeaders(request, builder);
        return builder;
    }
}
