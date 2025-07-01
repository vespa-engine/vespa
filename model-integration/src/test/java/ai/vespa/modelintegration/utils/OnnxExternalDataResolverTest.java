// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.modelintegration.utils;

import com.yahoo.config.ModelReference;
import com.yahoo.config.UrlReference;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author bjorncs
 */
class OnnxExternalDataResolverTest {
    @Test
    void downloads_external_data_files_for_onnx_model_referred_by_url() {
        var model = Paths.get("src/test/models/onnx/external_data/add_with_external_data.onnx");
        var externalDataFile = Paths.get("src/test/models/onnx/external_data/external_data.bin");
        var modelRef = ModelReference.unresolved(
                Optional.empty(),
                Optional.of(new UrlReference("https://my.website/add_with_external_data.onnx")),
                Optional.of("my-bearer-token"),
                Optional.empty());

        var modelPathHelper = mock(ModelPathHelper.class);
        when(modelPathHelper.getModelPathResolvingIfNecessary(modelRef)).thenReturn(model);
        var externalDataFileRef = ModelReference.unresolved(
                Optional.empty(),
                Optional.of(new UrlReference("https://my.website/external_data.bin")),
                Optional.of("my-bearer-token"),
                Optional.empty());

        when(modelPathHelper.getModelPathResolvingIfNecessary(externalDataFileRef)).thenReturn(externalDataFile);

        var resolver = new OnnxExternalDataResolver(modelPathHelper);
        var modelPath = resolver.resolveOnnxModel(modelRef);

        assertTrue(Files.exists(modelPath));
        assertTrue(Files.isSymbolicLink(modelPath));
        assertEquals("add_with_external_data.onnx", modelPath.getFileName().toString());

        var parentDir = modelPath.getParent();
        assertTrue(Files.exists(parentDir.resolve("external_data.bin")));
        assertTrue(Files.isSymbolicLink(parentDir.resolve("external_data.bin")));

        verify(modelPathHelper, times(1)).getModelPathResolvingIfNecessary(externalDataFileRef);
    }

    @Test
    void creates_directory_with_external_data_files() throws IOException {
        var model = Paths.get("src/test/models/onnx/external_data/add_with_external_data.onnx");
        var externalDataFile = Paths.get("src/test/models/onnx/external_data/external_data.bin");
        var externalDataFiles = Map.of(Path.of("external_files/external_data.bin"), externalDataFile);

        var tempDir = OnnxExternalDataResolver.createDirectoryWithExternalDataFiles(model, externalDataFiles);

        assertTrue(Files.exists(tempDir.resolve("add_with_external_data.onnx")));
        assertTrue(Files.isSymbolicLink(tempDir.resolve("add_with_external_data.onnx")));
        assertTrue(Files.exists(tempDir.resolve("external_files/external_data.bin")));
        assertTrue(Files.isSymbolicLink(tempDir.resolve("external_files/external_data.bin")));
    }
}
