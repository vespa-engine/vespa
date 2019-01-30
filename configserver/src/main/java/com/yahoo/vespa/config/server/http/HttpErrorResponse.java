// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.log.LogLevel;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;

import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Logger;

import static com.yahoo.jdisc.Response.Status.*;

/**
 * @author Ulf Lilleengen
 * @since 5.1
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
            log.log(LogLevel.INFO, "Returning response with response code " + code + ", error-code:" + errorType + ", message=" + msg);
        }
    }

    public enum errorCodes {
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

    public static HttpErrorResponse notFoundError(String msg) {
        return new HttpErrorResponse(NOT_FOUND, errorCodes.NOT_FOUND.name(), msg);
    }

    public static HttpErrorResponse internalServerError(String msg) {
        return new HttpErrorResponse(INTERNAL_SERVER_ERROR, errorCodes.INTERNAL_SERVER_ERROR.name(), msg);
    }

    public static HttpErrorResponse invalidApplicationPackage(String msg) {
        return new HttpErrorResponse(BAD_REQUEST, errorCodes.INVALID_APPLICATION_PACKAGE.name(), msg);
    }

    public static HttpErrorResponse outOfCapacity(String msg) {
        return new HttpErrorResponse(BAD_REQUEST, errorCodes.OUT_OF_CAPACITY.name(), msg);
    }

    public static HttpErrorResponse badRequest(String msg) {
        return new HttpErrorResponse(BAD_REQUEST, errorCodes.BAD_REQUEST.name(), msg);
    }

    public static HttpErrorResponse conflictWhenActivating(String msg) {
        return new HttpErrorResponse(CONFLICT, errorCodes.ACTIVATION_CONFLICT.name(), msg);
    }

    public static HttpErrorResponse methodNotAllowed(String msg) {
        return new HttpErrorResponse(METHOD_NOT_ALLOWED, errorCodes.METHOD_NOT_ALLOWED.name(), msg);
    }

    public static HttpResponse unknownVespaVersion(String message) {
        return new HttpErrorResponse(BAD_REQUEST, errorCodes.UNKNOWN_VESPA_VERSION.name(), message);
    }

    public static HttpResponse requestTimeout(String message) {
        return new HttpErrorResponse(REQUEST_TIMEOUT, errorCodes.REQUEST_TIMEOUT.name(), message);
    }

    public static HttpErrorResponse applicationLockFailure(String msg) {
        return new HttpErrorResponse(INTERNAL_SERVER_ERROR, errorCodes.APPLICATION_LOCK_FAILURE.name(), msg);
    }

    public static HttpErrorResponse parentHostNotReady(String msg) {
        return new HttpErrorResponse(BAD_REQUEST, errorCodes.PARENT_HOST_NOT_READY.name(), msg);
    }

    @Override
    public void render(OutputStream stream) throws IOException {
        new JsonFormat(true).encode(stream, slime);
    }

    //@Override
    public String getContentType() {
        return HttpConfigResponse.JSON_CONTENT_TYPE;
    }

}
