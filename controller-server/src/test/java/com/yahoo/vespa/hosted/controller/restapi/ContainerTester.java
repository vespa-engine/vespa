// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi;

import com.yahoo.application.container.JDisc;
import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Response;
import com.yahoo.collections.Pair;
import com.yahoo.component.Version;
import com.yahoo.io.IOUtils;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.Type;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

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
        Response response = container.handleRequest(request);
        Slime expectedSlime = SlimeUtils.jsonToSlime(expectedResponse.getBytes(StandardCharsets.UTF_8));
        Set<String> fieldsToCensor = fieldsToCensor(null, expectedSlime.get(), new HashSet<>());
        Slime responseSlime = SlimeUtils.jsonToSlime(response.getBody());
        List<Pair<String,String>> replaceStrings = new ArrayList<>();
        buildReplaceStrings(null, responseSlime.get(), fieldsToCensor, replaceStrings);

        String body = response.getBodyAsString();
        assertEquals("Status code. Response body was: " + body, expectedStatusCode, response.getStatus());
        assertEquals(responseFile.toString(), new String(SlimeUtils.toJsonBytes(expectedSlime), StandardCharsets.UTF_8),
                     replace(new String(SlimeUtils.toJsonBytes(responseSlime), StandardCharsets.UTF_8), replaceStrings));
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
        Response response = container.handleRequest(request);
        assertEquals(expectedResponse, response.getBodyAsString());
        assertEquals("Status code", expectedStatusCode, response.getStatus());
    }

    private Set<String> fieldsToCensor(String fieldNameOrNull, Inspector value, Set<String> fieldsToCensor) {
        switch (value.type()) {
            case ARRAY:  value.traverse((ArrayTraverser)(int index, Inspector element) -> fieldsToCensor(null, element, fieldsToCensor)); break;
            case OBJECT: value.traverse((String fieldName, Inspector fieldValue) -> fieldsToCensor(fieldName, fieldValue, fieldsToCensor)); break;
            case STRING: if (fieldNameOrNull != null && "(ignore)".equals(value.asString())) fieldsToCensor.add(fieldNameOrNull); break;
        }
        return fieldsToCensor;
    }

    private void buildReplaceStrings(String fieldNameOrNull, Inspector value, Set<String> fieldsToCensor,
                                     List<Pair<String,String>> replaceStrings) {
        switch (value.type()) {
            case ARRAY:  value.traverse((ArrayTraverser)(int index, Inspector element) -> buildReplaceStrings(null, element, fieldsToCensor, replaceStrings)); break;
            case OBJECT: value.traverse((String fieldName, Inspector fieldValue) -> buildReplaceStrings(fieldName, fieldValue, fieldsToCensor, replaceStrings)); break;
            default: replaceString(fieldNameOrNull, value, fieldsToCensor, replaceStrings);
        }
    }

    private void replaceString(String fieldName, Inspector fieldValue,
                               Set<String> fieldsToCensor, List<Pair<String,String>> replaceStrings) {
        if (fieldName == null) return;
        if ( ! fieldsToCensor.contains(fieldName)) return;

        String fromString;
        if ( fieldValue.type().equals(Type.STRING))
            fromString = "\"" + fieldName + "\":\"" + fieldValue.asString() + "\"";
        else if ( fieldValue.type().equals(Type.LONG))
            fromString = "\"" + fieldName + "\":" + fieldValue.asLong();
        else
            throw new IllegalArgumentException("Can only censor strings and longs");
        String toString = "\"" + fieldName + "\":\"(ignore)\"";
        replaceStrings.add(new Pair<>(fromString, toString));
    }

    private String replace(String json, List<Pair<String,String>> replaceStrings) {
        for (Pair<String,String> replaceString : replaceStrings)
            json = json.replace(replaceString.getFirst(), replaceString.getSecond());
        return json;
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

}
    
