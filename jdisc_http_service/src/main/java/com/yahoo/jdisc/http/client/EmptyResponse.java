// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.client;

import com.ning.http.client.cookie.Cookie;
import com.ning.http.client.FluentCaseInsensitiveStringsMap;
import com.ning.http.client.Response;
import com.ning.http.client.uri.Uri;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 * @since 2.0
 */
final class EmptyResponse implements Response {

    public static final EmptyResponse INSTANCE = new EmptyResponse();

    private EmptyResponse() {
        // hide
    }

    @Override
    public int getStatusCode() {
        return 0;
    }

    @Override
    public String getStatusText() {
        return null;
    }

    @Override
    public ByteBuffer getResponseBodyAsByteBuffer() {
        return ByteBuffer.allocate(0);
    }

    @Override
    public byte[] getResponseBodyAsBytes() throws IOException {
        return new byte[0];
    }

    @Override
    public InputStream getResponseBodyAsStream() throws IOException {
        return null;
    }

    @Override
    public String getResponseBodyExcerpt(int maxLength, String charset) throws IOException {
        return null;
    }

    @Override
    public String getResponseBody(String charset) throws IOException {
        return null;
    }

    @Override
    public String getResponseBodyExcerpt(int maxLength) throws IOException {
        return null;
    }

    @Override
    public String getResponseBody() throws IOException {
        return null;
    }

    @Override
    public Uri getUri() {
        return null;
    }

    @Override
    public String getContentType() {
        return null;
    }

    @Override
    public String getHeader(String name) {
        return null;
    }

    @Override
    public List<String> getHeaders(String name) {
        return null;
    }

    @Override
    public FluentCaseInsensitiveStringsMap getHeaders() {
        return null;
    }

    @Override
    public boolean isRedirected() {
        return false;
    }

    @Override
    public List<Cookie> getCookies() {
        return null;
    }

    @Override
    public boolean hasResponseStatus() {
        return false;
    }

    @Override
    public boolean hasResponseHeaders() {
        return false;
    }

    @Override
    public boolean hasResponseBody() {
        return false;
    }
}
