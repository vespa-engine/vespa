// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.log.LogLevel;
import com.yahoo.config.provision.OutOfCapacityException;
import com.yahoo.yolean.Exceptions;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * Super class for http handlers, that takes care of checking valid
 * methods for a request. Handlers should subclass this method and
 * implement the handleMETHOD methods that it supports.
 *
 * @author hmusum
 * @since 5.1.14
 */
public class HttpHandler extends LoggingRequestHandler {

    public HttpHandler(Executor executor, AccessLog accessLog) {
        super(executor, accessLog);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        log.log(LogLevel.DEBUG, request.getMethod() + " " + request.getUri().toString());
        try {
            switch (request.getMethod()) {
                case POST:
                    return handlePOST(request);
                case GET:
                    return handleGET(request);
                case PUT:
                    return handlePUT(request);
                case DELETE:
                    return handleDELETE(request);
                default:
                    return createErrorResponse(request.getMethod());
            }
        } catch (NotFoundException e) {
            return HttpErrorResponse.notFoundError(getMessage(e, request));
        } catch (BadRequestException e) {
            return HttpErrorResponse.badRequest(getMessage(e, request));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return HttpErrorResponse.badRequest(getMessage(e, request));
        } catch (InvalidApplicationException e) {
            return HttpErrorResponse.invalidApplicationPackage(getMessage(e, request));
        } catch (OutOfCapacityException e) {
            return HttpErrorResponse.outOfCapacity(getMessage(e, request));
        } catch (InternalServerException e) {
            return HttpErrorResponse.internalServerError(getMessage(e, request));
        } catch (UnknownVespaVersionException e) {
            return HttpErrorResponse.unknownVespaVersion(getMessage(e, request));
        } catch (Exception e) {
            e.printStackTrace();
            return HttpErrorResponse.internalServerError(getMessage(e, request));
        }
    }

    // Override default, since we need a higher timeout
    // TODO: Make configurable? Should be higher than timeouts used by clients
    @Override
    public Duration getTimeout() {
        return Duration.ofSeconds(910);
    }

    private String getMessage(Exception e, HttpRequest request) {
        String message;
        if (request.getBooleanProperty("debug")) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            message = sw.toString();
        } else {
            message = Exceptions.toMessageString(e);
        }
        return message;
    }

    /**
     * Default implementation of handler for GET requests. Returns an error response.
     * Override this method to handle GET requests.
     *
     * @param request a {@link HttpRequest}
     * @return an error response with response code 405
     */
    protected HttpResponse handleGET(HttpRequest request) {
        return createErrorResponse(request.getMethod());
    }

    /**
     * Default implementation of handler for POST requests. Returns an error response.
     * Override this method to handle POST requests.
     *
     * @param request a {@link HttpRequest}
     * @return an error response with response code 405
     */
    protected HttpResponse handlePOST(HttpRequest request) {
        return createErrorResponse(request.getMethod());
    }

    /**
     * Default implementation of handler for PUT requests. Returns an error response.
     * Override this method to handle POST requests.
     *
     * @param request a {@link HttpRequest}
     * @return an error response with response code 405
     */
    protected HttpResponse handlePUT(HttpRequest request) {
        return createErrorResponse(request.getMethod());
    }

    /**
     * Default implementation of handler for DELETE requests. Returns an error response.
     * Override this method to handle DELETE requests.
     *
     * @param request a {@link HttpRequest}
     * @return an error response with response code 405
     */
    protected HttpResponse handleDELETE(HttpRequest request) {
        return createErrorResponse(request.getMethod());
    }

    /**
     * Creates error response when method is not handled
     *
     * @return an error response with response code 405
     */
    private HttpResponse createErrorResponse(com.yahoo.jdisc.http.HttpRequest.Method method) {
        return HttpErrorResponse.methodNotAllowed("Method '" + method + "' is not supported");
    }
}
