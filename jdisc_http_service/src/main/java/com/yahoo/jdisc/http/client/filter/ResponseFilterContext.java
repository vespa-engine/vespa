// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.client.filter;

import com.google.common.collect.ImmutableMap;
import com.ning.http.client.FluentCaseInsensitiveStringsMap;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:alain@yahoo-inc.com">Alain Wan Buen Cheong</a>
 */
public class ResponseFilterContext {

    private final FluentCaseInsensitiveStringsMap headers = new FluentCaseInsensitiveStringsMap();
    private final Map<String, Object> requestContext;
    private int statusCode;
    private URI uri;

    private ResponseFilterContext(Builder builder) {
        this.statusCode = builder.statusCode;
        this.uri = builder.uri;
        this.headers.putAll(builder.headers);
        requestContext = ImmutableMap.copyOf(builder.requestContext);
    }

    public URI getRequestURI() {
        return uri;
    }

    public Map<String, Object> getRequestContext() { return requestContext; }

    public String getResponseFirstHeader(String key) {
        return headers.getFirstValue(key);
    }

    public int getResponseStatusCode() {
        return statusCode;
    }

    public static class Builder {

        private final FluentCaseInsensitiveStringsMap headers = new FluentCaseInsensitiveStringsMap();
        private final Map<String, Object> requestContext = new HashMap<>();
        private int statusCode;
        private URI uri;

        public Builder() {
        }

        public Builder statusCode(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public Builder headers(FluentCaseInsensitiveStringsMap headers) {
            this.headers.putAll(headers);
            return this;
        }

        public Builder uri(URI uri) {
            this.uri = uri;
            return this;
        }

        public Builder requestContext(Map<String, Object> requestContext) {
            this.requestContext.putAll(requestContext);
            return this;
        }

        public ResponseFilterContext build() {
            return new ResponseFilterContext(this);
        }

    }

}
