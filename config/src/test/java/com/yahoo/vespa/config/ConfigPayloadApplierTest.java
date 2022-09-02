// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.config.FileReference;
import com.yahoo.config.ModelReference;
import com.yahoo.config.ResolvedTypesConfig;
import com.yahoo.config.UrlReference;
import org.junit.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

/**
 * @author bratseth
 */
public class ConfigPayloadApplierTest {

    @Test
    public void testConfigApplier() {
        var applier = new ConfigPayloadApplier<>(new ResolvedTypesConfig.Builder(), new MockAcquirer(), new MockDownloader());
        var config = new ResolvedTypesConfig.Builder();
        config.myPath(new FileReference("mock/myPath.txt"));
        config.myUrl(new UrlReference("mock/myUrl.txt"));
        config.myModel(new ModelReference(Optional.empty(),
                                          Optional.of(new UrlReference("mockPath/myPath.txt")),
                                          Optional.of(new FileReference("mockUrl/myUrl.txt"))));
        applier.applyPayload(ConfigPayload.fromInstance(config.build()));
    }

    private static class MockAcquirer implements ConfigTransformer.PathAcquirer {

        @Override
        public Path getPath(FileReference fileReference) {
            return Path.of("mockPath", fileReference.value());
        }

    }

    private static class MockDownloader extends UrlDownloader {

        @Override
        public File waitFor(UrlReference urlReference, long timeout) {
            return new File("mockUrl", urlReference.value());
        }

    }

}
