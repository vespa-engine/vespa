// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.feature;

import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FeatureFlagTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testEnable() throws IOException {
        Path featureDirectory = folder.newFolder().toPath();
        FeatureConfigSource backend = new FeatureConfigSource(featureDirectory);

        FeatureFlag flag = new FeatureFlag(backend, "id");
        assertFalse(flag.enabled());

        UnixPath unixPath = new UnixPath(featureDirectory.resolve("id.json"));
        unixPath.writeUtf8File("{\"enabled\": false}");
        assertFalse(flag.enabled());

        unixPath.writeUtf8File("{\"enabled\": true}");
        assertTrue(flag.enabled());

        // writing garbage fails
        unixPath.writeUtf8File("{\"enable");
        try {
            flag.enabled();
            fail();
        } catch (UncheckedIOException e) {
            // OK
        }

        unixPath.toPath().toFile().delete();
        assertFalse(flag.enabled());
    }
}