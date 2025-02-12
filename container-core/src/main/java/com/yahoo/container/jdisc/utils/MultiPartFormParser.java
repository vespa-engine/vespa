// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.utils;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.yolean.Exceptions;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.MultiPart;
import org.eclipse.jetty.http.MultiPartConfig;
import org.eclipse.jetty.http.MultiPartFormData;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.util.Attributes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;

/**
 * Wrapper around Jetty's {@link MultiPart}.
 *
 * @author bjorncs
 */
public class MultiPartFormParser {

    private final InputStream input;
    private final String contentType;

    public MultiPartFormParser(InputStream in, String contentType) {
        this.input = in;
        this.contentType = contentType;
    }

    public MultiPartFormParser(HttpRequest request) { this(request.getData(), request.getHeader("Content-Type")); }

    public Map<String, PartItem> readParts() throws MultiPartException {
        try {
            var multipart = MultiPartFormData.getParts(
                Content.Source.from(input),
                new Attributes.Mapped(),
                contentType,
                new MultiPartConfig.Builder().build());

            Map<String, PartItem> result = new TreeMap<>();
            for (var part : multipart) {
                var item = new PartItem(
                    part.getName(),
                    Content.Source.asInputStream(part.getContentSource()),
                    part.getHeaders().get(HttpHeader.CONTENT_TYPE));
                result.put(part.getName(), item);
            }
            return result;
        } catch (Exception e) {
            throw new MultiPartException(e);
        }
    }

    public static class PartItem {
        private final String name;
        private final InputStream data;
        private final String contentType;

        public PartItem(String name, InputStream data, String contentType) {
            this.name = name;
            this.data = data;
            this.contentType = contentType;
        }

        public String name() { return name; }
        public InputStream data() { return data; }
        public String contentType() { return contentType; }
        @Override public String toString() { return "PartItem{" + "name='" + name + '\'' + ", contentType='" + contentType + '\'' + '}'; }
    }

    public static class MultiPartException extends IOException {
        public MultiPartException(Throwable t) { super(Exceptions.toMessageString(t), t); }
    }

}
