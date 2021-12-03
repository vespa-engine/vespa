// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import com.yahoo.jdisc.HeaderFields;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.Cookie;
import com.yahoo.jdisc.http.CookieHelper;
import com.yahoo.jdisc.http.HttpResponse;
import com.yahoo.jdisc.http.servlet.ServletOrJdiscHttpResponse;

import java.util.List;
import java.util.Map;

/**
 * JDisc implementation of a filter request.
 */
class JdiscFilterResponse extends DiscFilterResponse {

    private final Response parent;

    JdiscFilterResponse(Response parent) {
        // A separate adapter is required as DiscFilterResponse will invoke methods from ServletOrJdiscHttpResponse parameter in its constructor
        super(parent instanceof HttpResponse ? (HttpResponse)parent : new Adapter(parent));
        this.parent = parent;
    }

    @Override
    public void setStatus(int status) {
        parent.setStatus(status);
    }

    @Override
    public void setHeader(String name, String value) {
        parent.headers().put(name, value);
    }

    @Override
    public void removeHeaders(String name) {
        parent.headers().remove(name);
    }

    @Override
    public void setHeaders(String name, String value) {
        parent.headers().put(name, value);
    }

    @Override
    public void setHeaders(String name, List<String> values) {
        parent.headers().put(name, values);
    }

    @Override
    public void addHeader(String name, String value) {
        parent.headers().add(name, value);
    }

    @Override
    public String getHeader(String name) {
        List<String> values = parent.headers().get(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(values.size() - 1);
    }

    @Override
    public void setCookies(List<Cookie> cookies) {
        CookieHelper.encodeSetCookieHeader(parent.headers(), cookies);
    }

    private static class Adapter implements ServletOrJdiscHttpResponse {
        private final Response response;

        Adapter(Response response) {
            this.response = response;
        }

        @Override public void copyHeaders(HeaderFields target) { target.addAll(response.headers()); }
        @Override public int getStatus() { return response.getStatus(); }
        @Override public Map<String, Object> context() { return response.context(); }
        @Override public List<Cookie> decodeSetCookieHeader() { return CookieHelper.decodeSetCookieHeader(response.headers()); }
    }

}
