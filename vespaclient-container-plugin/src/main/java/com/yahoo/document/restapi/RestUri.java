// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
 * @author dybis
 */
public class RestUri {

    public static final char NUMBER_STREAMING = 'n';
    public static final char GROUP_STREAMING = 'g';
    public static final String DOCUMENT = "document";
    public static final String V_1 = "v1";
    public static final String ID = "id:";

    public enum apiErrorCodes {
        ERROR_ID_BASIC_USAGE(-1),
        ERROR_ID_DECODING_PATH(-2),
        VISITOR_ERROR(-3),
        NO_ROUTE_WHEN_NOT_PART_OF_MESSAGEBUS(-4),
        SEVERAL_CLUSTERS(-5),
        URL_PARSING(-6),
        INVALID_CREATE_VALUE(-7),
        TOO_MANY_PARALLEL_REQUESTS(-8),
        MISSING_CLUSTER(-9), UNKNOWN_BUCKET_SPACE(-9), INTERNAL_EXCEPTION(-9),
        DOCUMENT_CONDITION_NOT_MET(-10),
        DOCUMENT_EXCPETION(-11),
        PARSER_ERROR(-11),
        GROUP_AND_EXPRESSION_ERROR(-12),
        TIME_OUT(-13),
        INTERRUPTED(-14),
        UNSPECIFIED(-15);

        public final long value;
        apiErrorCodes(long value) {
            this.value = value;
        }
    }

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
        public static final long ERROR_ID_DECODING_PATH = -10L;
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
            String nextToken = rawParts.get(readPos++);
            return urlDecodeOrException(nextToken);
        }

        String restOfPath() throws RestApiException {
            String rawId = Joiner.on("/").join(rawParts.listIterator(readPos));
            return urlDecodeOrException(rawId);
        }

        String urlDecodeOrException(String url) throws RestApiException {
            try {
                return URLDecoder.decode(url, StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
                throw new RestApiException(Response.createErrorResponse(BAD_REQUEST,"Problems decoding the URI: " + e.getMessage(), apiErrorCodes.ERROR_ID_DECODING_PATH));
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
            case "docid":
                group = Optional.empty();
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
                "Expected: " +
                        ".../{namespace}/{document-type}/group/{name}/[{user-specified}]   " +
                        ".../{namespace}/{document-type}/docid/[{user-specified}]  : but got " + inputPath, apiErrorCodes.ERROR_ID_BASIC_USAGE));
    }

}

