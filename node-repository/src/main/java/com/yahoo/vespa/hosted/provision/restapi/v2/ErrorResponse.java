// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.JsonFormat;

import java.io.IOException;
import java.io.OutputStream;

import static com.yahoo.jdisc.Response.Status.*;

/**
 * Error responses with JSON bodies
 * 
 * @author bratseth
 */
public class ErrorResponse extends HttpResponse {

    private final Slime slime = new Slime();
    private final String message;

    public enum ErrorCode {
        FORBIDDEN,
        UNAUTHORIZED,
        NOT_FOUND,
        BAD_REQUEST,
        METHOD_NOT_ALLOWED,
        INTERNAL_SERVER_ERROR
    }

    private ErrorResponse(int code, ErrorCode errorCode, String message) {
        super(code);
        this.message = message;
        Cursor root = slime.setObject();
        root.setString("error-code", errorCode.name());
        root.setString("message", message);
    }

    public String message() { return message; }

    public static ErrorResponse notFoundError(String message) {
        return new ErrorResponse(NOT_FOUND, ErrorCode.NOT_FOUND, message);
    }

    public static ErrorResponse internalServerError(String message) {
        return new ErrorResponse(INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR, message);
    }

    public static ErrorResponse badRequest(String message) {
        return new ErrorResponse(BAD_REQUEST, ErrorCode.BAD_REQUEST, message);
    }

    public static ErrorResponse methodNotAllowed(String message) {
        return new ErrorResponse(METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED, message);
    }

    public static ErrorResponse unauthorized(String message) {
        return new ErrorResponse(UNAUTHORIZED, ErrorCode.UNAUTHORIZED, message);
    }

    public static ErrorResponse forbidden(String message) {
        return new ErrorResponse(FORBIDDEN, ErrorCode.FORBIDDEN, message);
    }

    @Override
    public void render(OutputStream stream) throws IOException {
        new JsonFormat(true).encode(stream, slime);
    }

    @Override
    public String getContentType() { return "application/json"; }

}
