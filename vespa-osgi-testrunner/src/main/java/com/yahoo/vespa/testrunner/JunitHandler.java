// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.testrunner;

import ai.vespa.hosted.api.TestDescriptor;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.yahoo.container.handler.metrics.JsonResponse;
import com.yahoo.container.jdisc.EmptyResponse;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.jdisc.Response;
import com.yahoo.restapi.ErrorResponse;

import java.util.concurrent.Executor;

/**
 * @author mortent
 */
public class JunitHandler extends LoggingRequestHandler {

    private final JunitRunner junitRunner;

    @Inject
    public JunitHandler(Executor executor, AccessLog accessLog, JunitRunner junitRunner) {
        super(executor, accessLog);
        this.junitRunner = junitRunner;
    }

    @Override
    public HttpResponse handle(HttpRequest httpRequest) {
        switch (httpRequest.getMethod()) {
            case GET:
                return handleGet(httpRequest);
            case POST:
                return handlePost(httpRequest);
            default:
                return new ErrorResponse(Response.Status.METHOD_NOT_ALLOWED, "testruntime-initialization", "Method '" + httpRequest.getMethod() + "' not supported");
        }
    }

    public HttpResponse handleGet(HttpRequest httpRequest) {
        String path = httpRequest.getUri().getPath();
        if (path.equals("/tester/v2/supported")) {
            return new JsonResponse(200, String.format("{\"supported\":%b}", junitRunner.isSupported()));
        }
        return new EmptyResponse(Response.Status.NOT_FOUND);
    }

    public HttpResponse handlePost(HttpRequest httpRequest) {
        String path = httpRequest.getUri().getPath();
        if (path.equals("/tester/v2/execute")) {
            TestDescriptor.TestCategory category = getCategoryOrDefault(httpRequest, TestDescriptor.TestCategory.systemtest);
            try {
                byte[] config = httpRequest.getData().readAllBytes();
                String jsonResponse = junitRunner.executeTests(category, config);
                return new JsonResponse(200, jsonResponse);
            } catch (Exception e) {
                return new ErrorResponse(500, "testrun", "Exception while executing tests");
            }
        }
        return new EmptyResponse(Response.Status.NOT_FOUND);
    }

    private static TestDescriptor.TestCategory getCategoryOrDefault(HttpRequest request, TestDescriptor.TestCategory defaultValue) {
        final String propertyString = request.getProperty("category");
        if (Strings.isNullOrEmpty(propertyString)) {
            return defaultValue;
        } else {
            try {
                return TestDescriptor.TestCategory.valueOf(propertyString);
            } catch (IllegalArgumentException ignored) {
                return defaultValue;
            }
        }
    }
}
