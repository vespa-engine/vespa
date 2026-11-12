// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.modelintegration.utils;

import com.yahoo.config.ModelReference;
import com.yahoo.config.UrlReference;
import onnx.Onnx;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author bjorncs
 * @author glebashnik
 */
class OnnxExternalDataResolverTest {
    @Test
    void downloads_external_data_files_for_onnx_model_referred_by_url() throws IOException {
        var model = Paths.get("src/test/models/onnx/external_data/add_with_external_data.onnx");
        var externalDataFile = Paths.get("src/test/models/onnx/external_data/external_data.bin");
        var modelRef = ModelReference.unresolved(
                Optional.empty(),
                Optional.of(new UrlReference("https://my.website.invalid/add_with_external_data.onnx")),
                Optional.of("my-bearer-token"),
                Optional.empty());

        var modelPathHelper = mock(ModelPathHelper.class);
        when(modelPathHelper.getModelPathResolvingIfNecessary(modelRef)).thenReturn(model);
        var externalDataFileRef = ModelReference.unresolved(
                Optional.empty(),
                Optional.of(new UrlReference("https://my.website.invalid/external_data.bin")),
                Optional.of("my-bearer-token"),
                Optional.empty());

        when(modelPathHelper.getModelPathResolvingIfNecessary(externalDataFileRef)).thenReturn(externalDataFile);

        var resolver = new OnnxExternalDataResolver(modelPathHelper);
        var modelPath = resolver.resolveOnnxModel(modelRef);
        var tempDir = modelPath.getParent();
        // Guard against accidentally deleting the source tree if resolveOnnxModel ever falls back to the local path.
        assertTrue(tempDir.getFileName().toString().startsWith("onnx-model-"));
        try {
            // Files are hard-linked (or copied, on a different filesystem) rather than symlinked, so that
            // onnxruntime's external data path validation sees them inside the model directory.
            assertTrue(Files.exists(modelPath));
            assertFalse(Files.isSymbolicLink(modelPath));
            assertTrue(Files.isRegularFile(modelPath));
            assertEquals("add_with_external_data.onnx", modelPath.getFileName().toString());

            var linkedDataFile = tempDir.resolve("external_data.bin");
            assertTrue(Files.exists(linkedDataFile));
            assertFalse(Files.isSymbolicLink(linkedDataFile));
            assertTrue(Files.isRegularFile(linkedDataFile));

            verify(modelPathHelper, times(1)).getModelPathResolvingIfNecessary(externalDataFileRef);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    void rejects_invalid_external_data_locations(@TempDir Path directory) throws IOException {
        var downloadedDataFile = Files.writeString(directory.resolve("downloaded.bin"), "data");
        var outside = directory.resolve("outside");
        var absolute = outside.resolve("planted.bin").toAbsolutePath().toString();
        for (var location : List.of(absolute, "../weights.bin", "weights/../../weights.bin", "weights/../weights.bin",
                                    "..%2Fweights.bin", ".", "")) {
            var model = Files.write(directory.resolve("model.onnx"), modelWithExternalData(location));
            var modelRef = ModelReference.unresolved(
                    Optional.empty(), Optional.of(new UrlReference("https://my.website.invalid/model.onnx")),
                    Optional.empty(), Optional.empty());
            var modelPathHelper = mock(ModelPathHelper.class);
            // Every download succeeds, so that only the check of the location can reject the model.
            when(modelPathHelper.getModelPathResolvingIfNecessary(any())).thenReturn(downloadedDataFile);
            when(modelPathHelper.getModelPathResolvingIfNecessary(modelRef)).thenReturn(model);

            var resolver = new OnnxExternalDataResolver(modelPathHelper);
            var exception = assertThrows(IllegalArgumentException.class, () -> resolver.resolveOnnxModel(modelRef),
                                         "Location '" + location + "'");
            assertTrue(exception.getMessage().contains("must be a relative path inside the model directory"),
                       exception.getMessage());
            // Only the model is downloaded, none of its external data files.
            verify(modelPathHelper, times(1)).getModelPathResolvingIfNecessary(any());
            assertFalse(Files.exists(outside), "Location '" + location + "'");
        }
    }

    @Test
    void does_not_link_external_data_file_to_absolute_location(@TempDir Path directory) {
        var model = Paths.get("src/test/models/onnx/external_data/add_with_external_data.onnx");
        var externalDataFile = Paths.get("src/test/models/onnx/external_data/external_data.bin");
        var outside = directory.resolve("outside");
        var externalDataFiles = Map.of(outside.resolve("planted.bin").toAbsolutePath(), externalDataFile);

        assertThrows(IllegalArgumentException.class,
                     () -> OnnxExternalDataResolver.createDirectoryWithExternalDataFiles(model, externalDataFiles));
        assertFalse(Files.exists(outside));
    }

    @Test
    void creates_directory_with_external_data_files() throws IOException {
        var model = Paths.get("src/test/models/onnx/external_data/add_with_external_data.onnx");
        var externalDataFile = Paths.get("src/test/models/onnx/external_data/external_data.bin");
        var externalDataFiles = Map.of(Path.of("external_files/external_data.bin"), externalDataFile);

        var tempDir = OnnxExternalDataResolver.createDirectoryWithExternalDataFiles(model, externalDataFiles);
        try {
            var linkedModel = tempDir.resolve("add_with_external_data.onnx");
            assertTrue(Files.exists(linkedModel));
            assertFalse(Files.isSymbolicLink(linkedModel));
            assertTrue(Files.isRegularFile(linkedModel));

            var linkedDataFile = tempDir.resolve("external_files/external_data.bin");
            assertTrue(Files.exists(linkedDataFile));
            assertFalse(Files.isSymbolicLink(linkedDataFile));
            assertTrue(Files.isRegularFile(linkedDataFile));
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static byte[] modelWithExternalData(String location) {
        var tensor = Onnx.TensorProto.newBuilder()
                .setName("weights")
                .setDataLocation(Onnx.TensorProto.DataLocation.EXTERNAL)
                .addExternalData(Onnx.StringStringEntryProto.newBuilder().setKey("location").setValue(location));
        return Onnx.ModelProto.newBuilder()
                .setGraph(Onnx.GraphProto.newBuilder().addInitializer(tensor)).build().toByteArray();
    }

    private static void deleteRecursively(Path dir) throws IOException {
        // Only ever delete a generated temp directory, never the checked-in source tree.
        if (dir == null || !dir.getFileName().toString().startsWith("onnx-model-") || !Files.exists(dir)) return;
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
