// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.utils;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.yolean.Exceptions;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.MultiPart;
import org.eclipse.jetty.http.MultiPartCompliance;
import org.eclipse.jetty.http.MultiPartConfig;
import org.eclipse.jetty.http.MultiPartFormData;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.util.Attributes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/**
 * Wrapper around Jetty's {@link MultiPart}.
 *
 * @author bjorncs
 */
public class MultiPartFormParser {

    private final Path partsTempDir;
    private final long maxMemoryPartSize;

    public MultiPartFormParser(Path partsTempDir, long maxMemoryPartSize) {
        this.partsTempDir = partsTempDir;
        this.maxMemoryPartSize = maxMemoryPartSize;
    }

    public Map<String, PartItem> readParts(HttpRequest request) throws MultiPartException {
        return readParts(request.getData(), request.getHeader("Content-Type"));
    }

    public Map<String, PartItem> readParts(InputStream input, String contentType) throws MultiPartException {
        try {
            var multipart = MultiPartFormData.getParts(
                Content.Source.from(input),
                new Attributes.Mapped(),
                contentType,
                new MultiPartConfig.Builder()
                        .maxPartSize(-1)
                        .maxSize(-1)
                        .maxHeadersSize(-1)
                        .maxParts(-1)
                        .maxMemoryPartSize(maxMemoryPartSize)
                        .location(partsTempDir)
                        .complianceMode(MultiPartCompliance.RFC7578)
                        .build());

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
