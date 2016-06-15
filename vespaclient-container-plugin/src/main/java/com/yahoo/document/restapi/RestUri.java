// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import static com.yahoo.jdisc.Response.Status.*;

/**
 * Represents the request URI with its values.
 *
 * @author dybdahl
 */
public class RestUri {

    public static final char NUMBER_STREAMING = 'n';
    public static final char GROUP_STREAMING = 'g';
    public static final String DOCUMENT = "document";
    public static final String V_1 = "v1";
    public static final String ID = "id:";

    /**
     * Represents the "grouping" part of document id which can be used with streaming model.
     */
    public static class Group {
        public final char name;
        public final String value;
        Group(char name, String value) {
            this.name = name;
            this.value = value;
        }
    }
    private final String namespace;
    private final String documentType;
    private final String docId;
    private Optional<Group> group = Optional.empty();
    private final String rawPath;

    public String getRawPath() {
        return rawPath;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getDocId() {
        return docId;
    }

    public Optional<Group> getGroup() {
        return group;
    }

    public String generateFullId() {
        return ID + namespace + ":" + documentType + ":"
	    + (getGroup().isPresent() ? group.get().name + "=" + group.get().value : "")
	    + ":" + docId;
    }

    static class PathParser {
        final List<String> rawParts;
        final String originalPath;
        int readPos = 0;
        public PathParser(String path) {
            this.originalPath = path;
            this.rawParts = Splitter.on('/').splitToList(path);
        }
        String nextTokenOrException() throws RestApiException {
            if (readPos >= rawParts.size()) {
                throwUsage(originalPath);
            }
            return rawParts.get(readPos++);
        }

        String restOfPath() throws RestApiException {
            String rawId = Joiner.on("/").join(rawParts.listIterator(readPos));
            try {
                return URLDecoder.decode(rawId, StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
                throw new RestApiException(Response.createErrorResponse(BAD_REQUEST,"Problems decoding the URI: " + e.getMessage()));
            }
        }
    }

    public RestUri(URI uri) throws RestApiException {
        rawPath = uri.getRawPath();
        PathParser pathParser = new PathParser(rawPath);
        if (! pathParser.nextTokenOrException().equals("") ||
                ! pathParser.nextTokenOrException().equals(DOCUMENT) ||
                ! pathParser.nextTokenOrException().equals(V_1)) {
            throwUsage(uri.getRawPath());
        }
        namespace = pathParser.nextTokenOrException();
        documentType = pathParser.nextTokenOrException();
        switch (pathParser.nextTokenOrException()) {
            case "number":
                group = Optional.of(new Group(NUMBER_STREAMING, pathParser.nextTokenOrException()));
                break;
            case "docid": group = Optional.empty();
                break;
            case "group":
                group = Optional.of(new Group(GROUP_STREAMING, pathParser.nextTokenOrException()));
                break;
            default: throwUsage(uri.getRawPath());
        }
        docId = pathParser.restOfPath();
    }

    private static void throwUsage(String inputPath) throws RestApiException {
        throw new RestApiException(Response.createErrorResponse(BAD_REQUEST,
                "Expected:\n" +
                        ".../{namespace}/{document-type}/group/{name}/[{user-specified}]\n" +
                        ".../{namespace}/{document-type}/docid/[{user-specified}]\n: but got " + inputPath));
    }

}

