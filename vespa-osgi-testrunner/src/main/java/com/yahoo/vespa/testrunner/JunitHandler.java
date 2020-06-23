// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.testrunner;

import ai.vespa.hosted.api.TestDescriptor;
import ai.vespa.hosted.cd.internal.TestRuntimeProvider;
import com.google.inject.Inject;
import com.yahoo.container.handler.metrics.JsonResponse;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.MessageResponse;
import org.osgi.framework.Bundle;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * @author mortent
 */
public class JunitHandler extends LoggingRequestHandler {

    private final JunitRunner junitRunner;
    private final TestRuntimeProvider testRuntimeProvider;

    @Inject
    public JunitHandler(Executor executor, AccessLog accessLog, JunitRunner junitRunner, TestRuntimeProvider testRuntimeProvider) {
        super(executor, accessLog);
        this.junitRunner = junitRunner;
        this.testRuntimeProvider = testRuntimeProvider;
    }

    @Override
    public HttpResponse handle(HttpRequest httpRequest) {
        String mode = property("mode", "help", httpRequest, String::valueOf);
        TestDescriptor.TestCategory category = property("category", TestDescriptor.TestCategory.systemtest, httpRequest, TestDescriptor.TestCategory::valueOf);

        try {
            testRuntimeProvider.initialize(httpRequest.getData().readAllBytes());
        } catch (IOException e) {
            return new ErrorResponse(500, "testruntime-initialization", "Exception reading test config");
        }

        if ("help".equalsIgnoreCase(mode)) {
            return new MessageResponse("Accepted modes: \n help \n list \n execute");
        }

        if (!"list".equalsIgnoreCase(mode) && !"execute".equalsIgnoreCase(mode)) {
            return new ErrorResponse(400, "client error", "Unknown mode \"" + mode + "\"");
        }

        Bundle testBundle = junitRunner.findTestBundle("-tests");
        TestDescriptor testDescriptor = junitRunner.loadTestDescriptor(testBundle);
        List<Class<?>> testClasses = junitRunner.loadClasses(testBundle, testDescriptor, category);

        String jsonResponse = junitRunner.executeTests(testClasses);

        return new JsonResponse(200, jsonResponse);
    }

    private static <VAL> VAL property(String name, VAL defaultValue, HttpRequest request, Function<String, VAL> converter) {
        final String propertyString = request.getProperty(name);
        if (propertyString != null) {
            return converter.apply(propertyString);
        }
        return defaultValue;
    }
}
