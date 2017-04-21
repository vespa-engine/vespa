// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.core;

import com.ning.http.client.RequestBuilder;
import com.yahoo.jdisc.HeaderFields;
import org.jboss.netty.handler.codec.http.HttpChunkTrailer;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpResponse;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
public class HeaderFieldsUtil {

    private static final byte[] DELIM_BYTES = ": ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CRLF_BYTES = "\r\n".getBytes(StandardCharsets.UTF_8);
    private static final Set<String> IGNORED_HEADERS = new HashSet<>(Arrays.asList(
            HttpHeaders.Names.CONTENT_LENGTH,
            HttpHeaders.Names.TRANSFER_ENCODING));

    public static void copyHeaders(com.yahoo.jdisc.Response src, HttpResponse dst) {
        copyHeaderFields(src.headers(), newSimpleHeaders(dst));
    }

    public static void copyHeaders(com.yahoo.jdisc.Request src, RequestBuilder dst) {
        copyHeaderFields(src.headers(), newSimpleHeaders(dst));
    }

    public static void copyTrailers(com.yahoo.jdisc.Response src, HttpResponse dst) {
        copyTrailers(src, newSimpleHeaders(dst));
    }

    public static void copyTrailers(com.yahoo.jdisc.Response src, HttpChunkTrailer dst) {
        copyTrailers(src, newSimpleHeaders(dst));
    }

    public static void copyTrailers(com.yahoo.jdisc.Request src, RequestBuilder dst) {
        copyTrailers(src, newSimpleHeaders(dst));
    }

    public static void copyTrailers(com.yahoo.jdisc.Request src, ByteArrayOutputStream dst) {
        copyTrailers(src, newSimpleHeaders(dst));
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    public static void copyTrailers(com.yahoo.jdisc.Request src, SimpleHeaders dst) {
        if (!(src instanceof com.yahoo.jdisc.http.HttpRequest)) {
            return;
        }
        final HeaderFields trailers = ((com.yahoo.jdisc.http.HttpRequest)src).trailers();
        synchronized (trailers) {
            copyHeaderFields(trailers, dst);
        }
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    public static void copyTrailers(com.yahoo.jdisc.Response src, SimpleHeaders dst) {
        if (!(src instanceof com.yahoo.jdisc.http.HttpResponse)) {
            return;
        }
        final HeaderFields trailers = ((com.yahoo.jdisc.http.HttpResponse)src).trailers();
        synchronized (trailers) {
            copyHeaderFields(trailers, dst);
        }
    }

    private static void copyHeaderFields(HeaderFields src, SimpleHeaders dst) {
        for (Map.Entry<String, List<String>> entry : src.entrySet()) {
            String key = entry.getKey();
            if (key != null && !IGNORED_HEADERS.contains(key)) {
                if (entry.getValue() == null) {
                    dst.addHeader(key, "");
                    continue;
                }
                for (String value : entry.getValue()) {
                    dst.addHeader(key, value != null ? value : "");
                }
            }
        }
    }

    private static SimpleHeaders newSimpleHeaders(final RequestBuilder dst) {
        return new SimpleHeaders() {

            @Override
            public void addHeader(String name, String value) {
                dst.addHeader(name, value);
            }
        };
    }

    private static SimpleHeaders newSimpleHeaders(final ByteArrayOutputStream dst) {
        return new SimpleHeaders() {

            @Override
            public void addHeader(String name, String value) {
                safeWrite(name.getBytes(StandardCharsets.UTF_8));
                safeWrite(DELIM_BYTES);
                safeWrite(value.getBytes(StandardCharsets.UTF_8));
                safeWrite(CRLF_BYTES);
            }

            void safeWrite(byte[] buf) {
                dst.write(buf, 0, buf.length);
            }
        };
    }

    private static SimpleHeaders newSimpleHeaders(final HttpMessage dst) {
        return new SimpleHeaders() {

            @Override
            public void addHeader(String name, String value) {
                dst.addHeader(name, value);
            }
        };
    }

    private static SimpleHeaders newSimpleHeaders(final HttpChunkTrailer dst) {
        return new SimpleHeaders() {

            @Override
            public void addHeader(String name, String value) {
                dst.addHeader(name, value);
            }
        };
    }

    private static interface SimpleHeaders {

        public void addHeader(String name, String value);
    }
}
