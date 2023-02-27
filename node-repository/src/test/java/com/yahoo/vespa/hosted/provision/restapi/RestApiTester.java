// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.application.Networking;
import com.yahoo.application.container.JDisc;
import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Response;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.SystemName;
import com.yahoo.io.IOUtils;
import com.yahoo.vespa.hosted.provision.testutils.ContainerConfig;
import org.junit.ComparisonFailure;

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class RestApiTester {

    private final static String responsesPath = "src/test/java/com/yahoo/vespa/hosted/provision/restapi/responses/";

    private final JDisc container;

    public RestApiTester(SystemName systemName, CloudAccount defaultCloudAccount) {
        container = JDisc.fromServicesXml(ContainerConfig.servicesXmlV2(0, systemName, defaultCloudAccount), Networking.disable);
    }

    public void close() {
        if (container != null) container.close();
    }

    public JDisc container() { return container; }

    /** Asserts a particular response and 200 as response status */
    public void assertResponse(Request request, String responseMessage) throws IOException {
        assertResponse(request, 200, responseMessage);
    }

    public void assertResponse(Request request, int responseStatus, String responseMessage) throws IOException {
        Response response = container.handleRequest(request);
        // Compare both status and message at once for easier diagnosis
        assertEquals("status: " + responseStatus + "\nmessage: " + responseMessage,
                     "status: " + response.getStatus() + "\nmessage: " + response.getBodyAsString());
    }

    public void assertResponseContains(Request request, String responseSnippet) throws IOException {
        assertPartialResponse(request, responseSnippet, true);
    }

    public void assertPartialResponse(Request request, String responseSnippet, boolean match) throws IOException {
        String response = container.handleRequest(request).getBodyAsString();
        assertEquals(String.format("Expected response to " + (match ? " " : "not ") + "contain: %s\nResponse: %s",
                                   responseSnippet, response), match, response.contains(responseSnippet));
    }

    public void assertFile(Request request, String responseFile) throws IOException {
        String expectedResponse = IOUtils.readFile(new File(responsesPath + responseFile));
        expectedResponse = include(expectedResponse);
        expectedResponse = expectedResponse.replaceAll("(\"[^\"]*\")|\\s*", "$1"); // Remove whitespace
        String responseString = container.handleRequest(request).getBodyAsString();
        if (expectedResponse.contains("(ignore)")) {
            // Convert expected response to a literal pattern and replace any ignored field with a pattern that matches
            // until the first stop character
            String stopCharacters = "[^,:\\\\[\\\\]{}]";
            String expectedResponsePattern = Pattern.quote(expectedResponse)
                                                    .replaceAll("\"?\\(ignore\\)\"?", "\\\\E" +
                                                                                      stopCharacters + "*\\\\Q");
            if (!Pattern.matches(expectedResponsePattern, responseString)) {
                throw new ComparisonFailure(responseFile + " (with ignored fields)", expectedResponsePattern,
                                            responseString);
            }
        } else {
            assertEquals(responseFile, expectedResponse, responseString);
        }
    }

    /** Replaces @include(localFile) with the content of the file */
    public String include(String response) throws IOException {
        // Please don't look at this code
        int includeIndex = response.indexOf("@include(");
        if (includeIndex < 0) return response;
        String prefix = response.substring(0, includeIndex);
        String rest = response.substring(includeIndex + "@include(".length());
        int filenameEnd = rest.indexOf(")");
        String includeFileName = rest.substring(0, filenameEnd);
        String includedContent = IOUtils.readFile(new File(responsesPath + includeFileName));
        includedContent = include(includedContent);
        String postFix = rest.substring(filenameEnd + 1);
        postFix = include(postFix);
        return prefix + includedContent + postFix;
    }

}
