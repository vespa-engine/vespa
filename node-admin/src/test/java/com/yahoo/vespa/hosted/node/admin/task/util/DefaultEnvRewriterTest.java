// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bjorncs
 */
public class DefaultEnvRewriterTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static final Path EXAMPLE_FILE = Paths.get(DefaultEnvRewriterTest.class.getResource("/default-env-example.txt").getFile());
    private static final Path EXPECTED_RESULT_FILE = Paths.get(DefaultEnvRewriterTest.class.getResource("/default-env-rewritten.txt").getFile());

    @Test
    public void default_env_is_correctly_rewritten() throws IOException {
        Path tempFile = temporaryFolder.newFile().toPath();
        Files.copy(EXAMPLE_FILE, tempFile, REPLACE_EXISTING);

        DefaultEnvRewriter rewriter = new DefaultEnvRewriter();
        rewriter.addOverride("VESPA_HOSTNAME", "my-new-hostname");
        rewriter.addFallback("VESPA_CONFIGSERVER", "new-fallback-configserver");
        rewriter.addOverride("VESPA_TLS_CONFIG_FILE", "/override/path/to/config.file");

        boolean modified = rewriter.updateFile(tempFile);

        assertTrue(modified);
        assertEquals(Files.readString(EXPECTED_RESULT_FILE), Files.readString(tempFile));

        modified = rewriter.updateFile(tempFile);
        assertFalse(modified);
        assertEquals(Files.readString(EXPECTED_RESULT_FILE), Files.readString(tempFile));
    }

    @Test
    public void generates_default_env_content() throws IOException {
        DefaultEnvRewriter rewriter = new DefaultEnvRewriter();
        rewriter.addOverride("VESPA_HOSTNAME", "my-new-hostname");
        rewriter.addFallback("VESPA_CONFIGSERVER", "new-fallback-configserver");
        rewriter.addOverride("VESPA_TLS_CONFIG_FILE", "/override/path/to/config.file");
        rewriter.addUnset("VESPA_LEGACY_OPTION");
        String generatedContent = rewriter.generateContent();
        assertEquals(Files.readString(EXPECTED_RESULT_FILE), generatedContent);
    }
}