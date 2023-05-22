// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;

import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.jdisc.Response.Status.BAD_REQUEST;
import static com.yahoo.jdisc.Response.Status.CONFLICT;
import static com.yahoo.jdisc.Response.Status.INTERNAL_SERVER_ERROR;
import static com.yahoo.jdisc.Response.Status.METHOD_NOT_ALLOWED;
import static com.yahoo.jdisc.Response.Status.NOT_FOUND;
import static com.yahoo.jdisc.Response.Status.PRECONDITION_FAILED;
import static com.yahoo.jdisc.Response.Status.REQUEST_TIMEOUT;

/**
 * @author Ulf Lilleengen
 */
public class HttpErrorResponse extends HttpResponse {

    private static final Logger log = Logger.getLogger(HttpErrorResponse.class.getName());
    private final Slime slime = new Slime();

    public HttpErrorResponse(int code, final String errorType, final String msg) {
        super(code);
        final Cursor root = slime.setObject();
        root.setString("error-code", errorType);
        root.setString("message", msg);
        if (code != 200) {
            log.log(Level.INFO, "Returning response with response code " + code + ", error-code:" + errorType + ", message=" + msg);
        }
    }

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
        CONFIG_NOT_CONVERGED,
        REINDEXING_STATUS_UNAVAILABLE,
        PRECONDITION_FAILED,
        QUOTA_EXCEEDED
    }

    public static HttpErrorResponse notFoundError(String msg) {
        return new HttpErrorResponse(NOT_FOUND, ErrorCode.NOT_FOUND.name(), msg);
    }

    public static HttpErrorResponse internalServerError(String msg) {
        return new HttpErrorResponse(INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR.name(), msg);
    }

    public static HttpErrorResponse invalidApplicationPackage(String msg) {
        return new HttpErrorResponse(BAD_REQUEST, ErrorCode.INVALID_APPLICATION_PACKAGE.name(), msg);
    }

    public static HttpErrorResponse nodeAllocationFailure(String msg) {
        return new HttpErrorResponse(BAD_REQUEST, ErrorCode.NODE_ALLOCATION_FAILURE.name(), msg);
    }

    public static HttpErrorResponse badRequest(String msg) {
        return new HttpErrorResponse(BAD_REQUEST, ErrorCode.BAD_REQUEST.name(), msg);
    }

    public static HttpErrorResponse conflictWhenActivating(String msg) {
        return new HttpErrorResponse(CONFLICT, ErrorCode.ACTIVATION_CONFLICT.name(), msg);
    }

    public static HttpErrorResponse methodNotAllowed(String msg) {
        return new HttpErrorResponse(METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.name(), msg);
    }

    public static HttpResponse unknownVespaVersion(String message) {
        return new HttpErrorResponse(BAD_REQUEST, ErrorCode.UNKNOWN_VESPA_VERSION.name(), message);
    }

    public static HttpResponse requestTimeout(String message) {
        return new HttpErrorResponse(REQUEST_TIMEOUT, ErrorCode.REQUEST_TIMEOUT.name(), message);
    }

    public static HttpErrorResponse applicationLockFailure(String msg) {
        return new HttpErrorResponse(INTERNAL_SERVER_ERROR, ErrorCode.APPLICATION_LOCK_FAILURE.name(), msg);
    }

    public static HttpErrorResponse parentHostNotReady(String msg) {
        return new HttpErrorResponse(CONFLICT, ErrorCode.PARENT_HOST_NOT_READY.name(), msg);
    }

    public static HttpErrorResponse certificateNotReady(String msg) {
        return new HttpErrorResponse(CONFLICT, ErrorCode.CERTIFICATE_NOT_READY.name(), msg);
    }

    public static HttpErrorResponse configNotConverged(String msg) {
        return new HttpErrorResponse(CONFLICT, ErrorCode.CONFIG_NOT_CONVERGED.name(), msg);
    }

    public static HttpErrorResponse loadBalancerNotReady(String msg) {
        return new HttpErrorResponse(CONFLICT, ErrorCode.LOAD_BALANCER_NOT_READY.name(), msg);
    }

    public static HttpResponse reindexingStatusUnavailable(String msg) {
        return new HttpErrorResponse(CONFLICT, ErrorCode.REINDEXING_STATUS_UNAVAILABLE.name(), msg);
    }

    public static HttpResponse preconditionFailed(String msg) {
        return new HttpErrorResponse(PRECONDITION_FAILED, ErrorCode.PRECONDITION_FAILED.name(), msg);
    }

    public static HttpResponse quotaExceeded(String msg) {
        return new HttpErrorResponse(BAD_REQUEST, ErrorCode.QUOTA_EXCEEDED.name(), msg);
    }

    @Override
    public void render(OutputStream stream) throws IOException {
        new JsonFormat(true).encode(stream, slime);
    }

    @Override
    public String getContentType() {
        return HttpConfigResponse.JSON_CONTENT_TYPE;
    }

}
