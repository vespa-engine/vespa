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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class ConfigPayloadApplierTest {

    @Test
    public void testAllConfigApplierReferenceTypes() {
        var configBuilder = new ResolvedTypesConfig.Builder();
        var applier = new ConfigPayloadApplier<>(configBuilder, new MockAcquirer(), new MockDownloader());

        var inputConfig = new ResolvedTypesConfig.Builder();
        inputConfig.myPath(new FileReference("myPath.txt"));
        inputConfig.myUrl(new UrlReference("myUrl.txt"));
        inputConfig.myModel(ModelReference.unresolved(Optional.empty(),
                                                      Optional.of(new UrlReference("myUrl.txt")),
                                                      Optional.of(new FileReference("myPath.txt"))));
        applier.applyPayload(ConfigPayload.fromInstance(inputConfig.build()));
        var config = configBuilder.build();

        assertEndsWith("resolvedPath/myPath.txt", config.myPath().toString());
        assertEndsWith("resolvedUrl/myUrl.txt", config.myUrl().toString());
        assertEndsWith("resolvedUrl/myUrl.txt", config.myModel().toString());
    }

    @Test
    public void testModelWithUrlOnly() {
        var configBuilder = new ResolvedTypesConfig.Builder();
        var applier = new ConfigPayloadApplier<>(configBuilder, new MockAcquirer(), new MockDownloader());

        var inputConfig = new ResolvedTypesConfig.Builder();
        inputConfig.myPath(new FileReference("myPath.txt"));
        inputConfig.myUrl(new UrlReference("myUrl.txt"));
        inputConfig.myModel(ModelReference.valueOf("my-id myUrl.txt \"\""));
        applier.applyPayload(ConfigPayload.fromInstance(inputConfig.build()));
        var config = configBuilder.build();

        assertEndsWith("resolvedUrl/myUrl.txt", config.myModel().toString());
    }

    @Test
    public void testModelWithPathOnly() {
        var configBuilder = new ResolvedTypesConfig.Builder();
        var applier = new ConfigPayloadApplier<>(configBuilder, new MockAcquirer(), new MockDownloader());

        var inputConfig = new ResolvedTypesConfig.Builder();
        inputConfig.myPath(new FileReference("myPath.txt"));
        inputConfig.myUrl(new UrlReference("myUrl.txt"));
        inputConfig.myModel(ModelReference.valueOf("my-id \"\" myPath.txt"));
        applier.applyPayload(ConfigPayload.fromInstance(inputConfig.build()));
        var config = configBuilder.build();

        assertEndsWith("resolvedPath/myPath.txt", config.myModel().toString());
    }

    private void assertEndsWith(String ending, String string) {
        String assertingThat = "'" + string + "' ends with '" + ending + "'";
        try {
            assertEquals(assertingThat, ending, string.substring(string.length() - ending.length()));
        }
        catch (StringIndexOutOfBoundsException e) {
            fail(assertingThat);
        }
    }

    private static class MockAcquirer implements ConfigTransformer.PathAcquirer {

        @Override
        public Path getPath(FileReference fileReference) {
            return Path.of("resolvedPath", fileReference.value());
        }

    }

    private static class MockDownloader extends UrlDownloader {

        @Override
        public File waitFor(UrlReference urlReference, long timeout) {
            return new File("resolvedUrl", urlReference.value());
        }

    }

}
