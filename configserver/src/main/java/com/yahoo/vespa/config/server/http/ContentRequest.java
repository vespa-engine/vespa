// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.container.jdisc.HttpRequest;
import ai.vespa.http.HttpURL.Path;

import java.io.InputStream;

import static com.yahoo.vespa.config.server.session.Session.Mode;

/**
 * Represents a {@link ContentRequest}, and contains common functionality for content requests for all content handlers.
 *
 * @author Ulf Lilleengen
 * @since 5.3
 */
public abstract class ContentRequest {
    private static final String RETURN_QUERY_PROPERTY = "return";

    enum ReturnType {CONTENT, STATUS}

    private final long sessionId;
    private final Path path;
    private final ApplicationFile file;
    private final HttpRequest request;

    protected ContentRequest(HttpRequest request, long sessionId, Path path, ApplicationFile applicationFile) {
        this.request = request;
        this.sessionId = sessionId;
        this.path = path;
        this.file = applicationFile;
    }

    public static Mode getApplicationFileMode(com.yahoo.jdisc.http.HttpRequest.Method method) {
        switch (method) {
            case GET:
            case OPTIONS:
                return Mode.READ;
            default:
                return Mode.WRITE;
        }
    }

    ReturnType getReturnType() {
        if (request.hasProperty(RETURN_QUERY_PROPERTY)) {
            String type = request.getProperty(RETURN_QUERY_PROPERTY);
            switch (type) {
                case "content":
                return ReturnType.CONTENT;
                case "status":
                return ReturnType.STATUS;
                default:
                throw new BadRequestException("return=" + type + " is an illegal argument. Only " +
                    ReturnType.CONTENT.name() + " and " + ReturnType.STATUS.name() + " are allowed");
            }
        } else {
            return ReturnType.CONTENT;
        }
    }

    protected abstract String getPathPrefix();

    String getUrlBase(String appendStr) {
        return Utils.getUrlBase(request, getPathPrefix() + appendStr);
    }

    boolean isRecursive() {
        return request.getBooleanProperty("recursive");
    }

    boolean hasRequestBody() {
        return request.getData() != null;
    }

    InputStream getData() {
        return request.getData();
    }


    Path getPath() {
        return path;
    }

    ApplicationFile getFile() {
        return file;
    }

    ApplicationFile getExistingFile() {
        if ( ! file.exists()) {
            throw new NotFoundException("Session " + sessionId + " does not contain a file at " + path);
        }
        return file;
    }
}
