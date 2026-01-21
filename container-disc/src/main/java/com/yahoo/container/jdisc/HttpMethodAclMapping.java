// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.jdisc;

import com.yahoo.jdisc.http.HttpRequest;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.yahoo.jdisc.http.HttpRequest.Method.CONNECT;
import static com.yahoo.jdisc.http.HttpRequest.Method.DELETE;
import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static com.yahoo.jdisc.http.HttpRequest.Method.HEAD;
import static com.yahoo.jdisc.http.HttpRequest.Method.OPTIONS;
import static com.yahoo.jdisc.http.HttpRequest.Method.PATCH;
import static com.yahoo.jdisc.http.HttpRequest.Method.POST;
import static com.yahoo.jdisc.http.HttpRequest.Method.PUT;
import static com.yahoo.jdisc.http.HttpRequest.Method.TRACE;

/**
 * Acl Mapping based on http method.
 * Defaults to:<br>
 * {GET, HEAD, OPTIONS} -&gt; READ<br>
 * {POST, DELETE, PUT, PATCH, CONNECT, TRACE} -&gt; WRITE
 * @author mortent
 */
public class HttpMethodAclMapping implements AclMapping {

    private final Map<HttpRequest.Method, Action> mappings;

    private HttpMethodAclMapping(Map<HttpRequest.Method, Action> overrides) {
        HashMap<HttpRequest.Method, Action> tmp = new HashMap<>(defaultMappings());
        tmp.putAll(overrides);
        mappings = Map.copyOf(tmp);
    }

    private static Map<HttpRequest.Method, Action> defaultMappings() {
        return Map.of(GET, Action.READ,
                      HEAD, Action.READ,
                      OPTIONS, Action.READ,
                      POST, Action.WRITE,
                      DELETE, Action.WRITE,
                      PUT, Action.WRITE,
                      PATCH, Action.WRITE,
                      CONNECT, Action.WRITE,
                      TRACE, Action.WRITE);
    }

    @Override
    public Action get(RequestView requestView) {
        return Optional.ofNullable(mappings.get(requestView.method()))
                .orElseThrow(() -> new IllegalArgumentException("Illegal request method: " + requestView.method()));
    }

    public static HttpMethodAclMapping.Builder standard() {
        return new HttpMethodAclMapping.Builder();
    }

    public static class Builder {
        private final  Map<com.yahoo.jdisc.http.HttpRequest.Method, Action> overrides = new HashMap<>();
        public HttpMethodAclMapping.Builder override(HttpRequest.Method method, Action action) {
            overrides.put(method, action);
            return this;
        }
        public HttpMethodAclMapping build() {
            return new HttpMethodAclMapping(overrides);
        }
    }
}
