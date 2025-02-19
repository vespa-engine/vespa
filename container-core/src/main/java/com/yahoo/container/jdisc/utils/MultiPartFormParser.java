// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.utils;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.yolean.Exceptions;
import jakarta.servlet.http.Part;
import org.eclipse.jetty.server.MultiPartFormInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;

/**
 * Wrapper around Jetty's {@link MultiPartFormInputStream}.
 *
 * @author bjorncs
 */
public class MultiPartFormParser {

    private final MultiPartFormInputStream multipart;

    public MultiPartFormParser(InputStream in, String contentType) {
        this.multipart = new MultiPartFormInputStream(in, contentType, /*config*/null, /*contextTmpDir*/null);
    }

    public MultiPartFormParser(HttpRequest request) { this(request.getData(), request.getHeader("Content-Type")); }

    public Map<String, PartItem> readParts() throws MultiPartException {
        try {
            Map<String, PartItem> result = new TreeMap<>();
            for (Part servletPart : multipart.getParts()) {
                result.put(servletPart.getName(), new PartItem(servletPart));
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

        private PartItem(Part servletPart) throws IOException {
            this(servletPart.getName(), servletPart.getInputStream(), servletPart.getContentType());
        }

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
