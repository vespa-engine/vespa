// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi;

import com.yahoo.application.container.JDisc;
import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Response;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.Version;
import com.yahoo.container.http.filter.FilterChainRepository;
import com.yahoo.io.IOUtils;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
import com.yahoo.jdisc.http.filter.SecurityRequestFilterChain;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import org.junit.ComparisonFailure;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

/**
 * Provides testing of JSON container responses
 * 
 * @author bratseth
 */
public class ContainerTester {

    private final JDisc container;
    private final String responseFilePath;
    
    public ContainerTester(JDisc container, String responseFilePath) {
        this.container = container;
        this.responseFilePath = responseFilePath;
    }
    
    public JDisc container() { return container; }

    public Controller controller() {
        return (Controller) container.components().getComponent(Controller.class.getName());
    }

    public void updateSystemVersion() {
        controller().updateVersionStatus(VersionStatus.compute(controller()));
    }

    public void updateSystemVersion(Version version) {
        controller().updateVersionStatus(VersionStatus.compute(controller(), version));
    }

    public void assertResponse(Supplier<Request> request, File responseFile) throws IOException {
        assertResponse(request.get(), responseFile);
    }

    public void assertResponse(Request request, File responseFile) throws IOException {
        assertResponse(request, responseFile, 200);
    }

    public void assertResponse(Supplier<Request> request, File responseFile, int expectedStatusCode) throws IOException {
        assertResponse(request.get(), responseFile, expectedStatusCode);
    }

    public void assertResponse(Request request, File responseFile, int expectedStatusCode) throws IOException {
        String expectedResponse = IOUtils.readFile(new File(responseFilePath + responseFile.toString()));
        expectedResponse = include(expectedResponse);
        expectedResponse = expectedResponse.replaceAll("(\"[^\"]*\")|\\s*", "$1"); // Remove whitespace
        FilterResult filterResult = invokeSecurityFilters(request);
        request = filterResult.request;
        Response response = filterResult.response != null ? filterResult.response : container.handleRequest(request);
        String responseString = response.getBodyAsString();
        if (expectedResponse.contains("(ignore)")) {
            // Convert expected response to a literal pattern and replace any ignored field with a pattern that matches
            // anything
            String expectedResponsePattern = Pattern.quote(expectedResponse)
                                                    .replaceAll("\"?\\(ignore\\)\"?", "\\\\E.*\\\\Q");
            if (!Pattern.matches(expectedResponsePattern, responseString)) {
                throw new ComparisonFailure(responseFile.toString() + " (with ignored fields)",
                                            expectedResponsePattern, responseString);
            }
        } else {
            assertEquals(responseFile.toString(), expectedResponse, responseString);
        }
        assertEquals("Status code", expectedStatusCode, response.getStatus());
    }

    public void assertResponse(Supplier<Request> request, String expectedResponse) throws IOException {
        assertResponse(request.get(), expectedResponse, 200);
    }

    public void assertResponse(Request request, String expectedResponse) throws IOException {
        assertResponse(request, expectedResponse, 200);
    }

    public void assertResponse(Supplier<Request> request, String expectedResponse, int expectedStatusCode) throws IOException {
        assertResponse(request.get(), expectedResponse, expectedStatusCode);
    }

    public void assertResponse(Request request, String expectedResponse, int expectedStatusCode) throws IOException {
        FilterResult filterResult = invokeSecurityFilters(request);
        request = filterResult.request;
        Response response = filterResult.response != null ? filterResult.response : container.handleRequest(request);
        assertEquals(expectedResponse, response.getBodyAsString());
        assertEquals("Status code", expectedStatusCode, response.getStatus());
    }

    // Hack to run request filters as part of the request processing chain.
    // Limitation: Bindings ignored, disc filter request wrapper only support limited set of methods.
    private FilterResult invokeSecurityFilters(Request request) {
        FilterChainRepository filterChainRepository = (FilterChainRepository) container.components().getComponent(FilterChainRepository.class.getName());
        SecurityRequestFilterChain chain = (SecurityRequestFilterChain) filterChainRepository.getFilter(ComponentSpecification.fromString("default"));
        for (SecurityRequestFilter securityRequestFilter : chain.getFilters()) {
            ApplicationRequestToDiscFilterRequestWrapper discFilterRequest = new ApplicationRequestToDiscFilterRequestWrapper(request);
            ResponseHandlerToApplicationResponseWrapper responseHandlerWrapper = new ResponseHandlerToApplicationResponseWrapper();
            securityRequestFilter.filter(discFilterRequest, responseHandlerWrapper);
            request = discFilterRequest.getUpdatedRequest();
            Optional<Response> filterResponse = responseHandlerWrapper.toResponse();
            if (filterResponse.isPresent()) {
                return new FilterResult(request, filterResponse.get());
            }
        }
        return new FilterResult(request, null);
    }

    /** Replaces @include(localFile) with the content of the file */
    private String include(String response) throws IOException {
        // Please don't look at this code
        int includeIndex = response.indexOf("@include(");
        if (includeIndex < 0) return response;
        String prefix = response.substring(0, includeIndex);
        String rest = response.substring(includeIndex + "@include(".length());
        int filenameEnd = rest.indexOf(")");
        String includeFileName = rest.substring(0, filenameEnd);
        String includedContent = IOUtils.readFile(new File(responseFilePath + includeFileName));
        includedContent = include(includedContent);
        String postFix = rest.substring(filenameEnd + 1);
        postFix = include(postFix);
        return prefix + includedContent + postFix;
    }

    static class FilterResult {
        final Request request;
        final Response response;

        FilterResult(Request request, Response response) {
            this.request = request;
            this.response = response;
        }
    }
}
    
