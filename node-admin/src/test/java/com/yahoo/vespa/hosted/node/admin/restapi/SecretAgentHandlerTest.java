// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.restapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;

import java.util.regex.Pattern;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.Assert.*;

/**
 * @author valerijf
 */
public class SecretAgentHandlerTest {
    @Test
    public void testSecretAgentFormat() throws JsonProcessingException {
        SecretAgentHandler secretAgentHandler = new SecretAgentHandler();
        secretAgentHandler
                .withDimension("host", "host.name.test.yahoo.com")
                .withDimension("dimention", 6)
                .withMetric("runtime", 0.0254)
                .withMetric("memory", 321415L);

        String expectedJson = Pattern.quote("{\"application\":\"docker\",\"timestamp\":") +
                "[0-9]{10}" + // The timestamp is (currently) 10 digit long numbe, update to 11 on 20/11/2286
                Pattern.quote(",\"dimensions\":{\"host\":\"host.name.test.yahoo.com\",\"dimention\":6},\"metrics\":{\"memory\":321415,\"runtime\":0.0254}}");

        assertThat(secretAgentHandler.toJson(), matchesPattern(expectedJson));
    }
}
