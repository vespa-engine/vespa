// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.testrunner;

import com.yahoo.container.handler.metrics.JsonResponse;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.MessageResponse;
import org.osgi.framework.Bundle;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * @author mortent
 */
public class JunitHandler extends LoggingRequestHandler {

    private final JunitRunner junitRunner;

    public JunitHandler(Executor executor, AccessLog accessLog, JunitRunner junitRunner) {
        super(executor, accessLog);
        this.junitRunner = junitRunner;
    }

    @Override
    public HttpResponse handle(HttpRequest httpRequest) {
        String mode = property("mode", "help", httpRequest, String::valueOf);
        TestDescriptor.TestCategory category = property("category", TestDescriptor.TestCategory.systemtest, httpRequest, TestDescriptor.TestCategory::valueOf);

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
