// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.maintainer.restapi.v1;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;

import java.io.IOException;
import java.io.OutputStream;

import static com.yahoo.jdisc.Response.Status.BAD_REQUEST;
import static com.yahoo.jdisc.Response.Status.INTERNAL_SERVER_ERROR;
import static com.yahoo.jdisc.Response.Status.METHOD_NOT_ALLOWED;
import static com.yahoo.jdisc.Response.Status.NOT_FOUND;

public class ErrorResponse extends HttpResponse {

    private final Slime slime = new Slime();

    public enum errorCodes {
        NOT_FOUND,
        BAD_REQUEST,
        METHOD_NOT_ALLOWED,
        INTERNAL_SERVER_ERROR
    }

    public ErrorResponse(int code, String errorType, String message) {
        super(code);
        Cursor root = slime.setObject();
        root.setString("error-code", errorType);
        root.setString("message", message);
    }

    public static ErrorResponse notFoundError(String message) {
        return new ErrorResponse(NOT_FOUND, errorCodes.NOT_FOUND.name(), message);
    }

    public static ErrorResponse internalServerError(String message) {
        return new ErrorResponse(INTERNAL_SERVER_ERROR, errorCodes.INTERNAL_SERVER_ERROR.name(), message);
    }

    public static ErrorResponse badRequest(String message) {
        return new ErrorResponse(BAD_REQUEST, errorCodes.BAD_REQUEST.name(), message);
    }

    public static ErrorResponse methodNotAllowed(String message) {
        return new ErrorResponse(METHOD_NOT_ALLOWED, errorCodes.METHOD_NOT_ALLOWED.name(), message);
    }

    @Override
    public void render(OutputStream stream) throws IOException {
        new JsonFormat(true).encode(stream, slime);
    }

    @Override
    public String getContentType() { return "application/json"; }

}
