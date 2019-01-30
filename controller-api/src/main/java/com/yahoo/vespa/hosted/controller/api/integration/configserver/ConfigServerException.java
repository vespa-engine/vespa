// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import java.net.URI;

/**
 * @author Tony Vaagenes
 */
public class ConfigServerException extends RuntimeException {

    private final URI serverUri;
    private final ErrorCode errorCode;

    public ConfigServerException(URI serverUri, String message, ErrorCode errorCode, Throwable cause) {
        super(message, cause);
        this.serverUri = serverUri;
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public URI getServerUri() {
        return serverUri;
    }

    // TODO: Copied from Vespa. Expose these in Vespa and use them here
    public enum ErrorCode {
        APPLICATION_LOCK_FAILURE,
        BAD_REQUEST,
        ACTIVATION_CONFLICT,
        INTERNAL_SERVER_ERROR,
        INVALID_APPLICATION_PACKAGE,
        METHOD_NOT_ALLOWED,
        NOT_FOUND,
        OUT_OF_CAPACITY,
        REQUEST_TIMEOUT,
        UNKNOWN_VESPA_VERSION,
        PARENT_HOST_NOT_READY
    }

}
