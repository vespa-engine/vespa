// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;

/**
 * @author gjoranv
 */
public class PublicApiBundleValidatorTest {

    @TempDir
    public File tempDir;

    @Test
    void non_public_api_usage_is_picked_up_from_manifest() throws IOException {
        var outputBuf = new StringBuffer();
        var deployState = BundleValidatorTest.createDeployState(outputBuf);
        var jarFile = BundleValidatorTest.createTemporaryJarFile(tempDir, "non-public-api");

        var validator = new PublicApiBundleValidator();
        validator.validateJarFile(deployState, jarFile);

        String output = outputBuf.toString();
        assertThat(output, containsString("uses non-public Vespa APIs: ["));

        // List of packages should be sorted
        List<String> packages = Arrays.asList(output.substring(output.indexOf("[") + 1, output.indexOf("]")).split(", "));
        assertThat(packages, hasSize(2));
        assertThat(packages, contains("ai.vespa.lib.non_public", "com.yahoo.lib.non_public"));
    }

}
