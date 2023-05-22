// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

/**
 * An exception due to server error, a bad request, or similar.
 *
 * @author jonmv
 */
public class ConfigServerException extends RuntimeException {

    private final ErrorCode code;
    private final String message;

    public ConfigServerException(ErrorCode code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public ConfigServerException(ErrorCode code, String message, String context) {
        super(context + ": " + message);
        this.code = code;
        this.message = message;
    }

    public ErrorCode code() { return code; }

    public String message() { return message; }

    public enum ErrorCode {
        APPLICATION_LOCK_FAILURE,
        BAD_REQUEST,
        ACTIVATION_CONFLICT,
        INTERNAL_SERVER_ERROR,
        INVALID_APPLICATION_PACKAGE,
        METHOD_NOT_ALLOWED,
        NOT_FOUND,
        NODE_ALLOCATION_FAILURE,
        REQUEST_TIMEOUT,
        UNKNOWN_VESPA_VERSION,
        PARENT_HOST_NOT_READY,
        CERTIFICATE_NOT_READY,
        LOAD_BALANCER_NOT_READY,
        INCOMPLETE_RESPONSE,
        CONFIG_NOT_CONVERGED,
        QUOTA_EXCEEDED
    }

}
