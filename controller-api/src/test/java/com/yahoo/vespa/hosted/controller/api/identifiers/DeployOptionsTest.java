// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.identifiers;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployOptions;
import org.junit.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * @author mortent
 */
public class DeployOptionsTest {

    @Test
    public void it_serializes_version() throws IOException {
        DeployOptions options = new DeployOptions(Optional.empty(), Optional.of(new Version("6.98.227")), false, false);
        final ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .registerModule(new Jdk8Module());

        String string = objectMapper.writeValueAsString(options);
        assertEquals("{\"screwdriverBuildJob\":null,\"vespaVersion\":\"6.98.227\",\"ignoreValidationErrors\":false,\"deployCurrentVersion\":false}", string);
    }
}
