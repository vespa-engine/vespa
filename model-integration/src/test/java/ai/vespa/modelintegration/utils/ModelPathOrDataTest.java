// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * @author glebashnik
 */
class ModelPathOrDataTest {

    private static final Path EXTERNAL_DATA_DIR = Path.of("src/test/models/onnx/external_data");

    @Test
    void hash_depends_on_external_data_files(@TempDir Path directory) throws IOException {
        var model = Files.copy(EXTERNAL_DATA_DIR.resolve("add_with_external_data.onnx"),
                               directory.resolve("add_with_external_data.onnx"));
        var dataFile = Files.copy(EXTERNAL_DATA_DIR.resolve("external_data.bin"),
                                  directory.resolve("external_data.bin"));
        var originalHash = ModelPathOrData.of(model.toString()).calculateHash();
        assertEquals(originalHash, ModelPathOrData.of(model.toString()).calculateHash());

        var modified = Files.getLastModifiedTime(dataFile).toMillis() + 1000;
        Files.setLastModifiedTime(dataFile, FileTime.fromMillis(modified));
        var modifiedHash = ModelPathOrData.of(model.toString()).calculateHash();
        assertNotEquals(originalHash, modifiedHash);

        Files.write(dataFile, new byte[4], StandardOpenOption.APPEND);
        Files.setLastModifiedTime(dataFile, FileTime.fromMillis(modified));
        assertNotEquals(modifiedHash, ModelPathOrData.of(model.toString()).calculateHash());
    }

    @Test
    void hash_of_model_with_missing_external_data_file_differs_from_existing(@TempDir Path directory)
            throws IOException {
        var model = Files.copy(EXTERNAL_DATA_DIR.resolve("add_with_external_data.onnx"),
                               directory.resolve("add_with_external_data.onnx"));
        var missingHash = ModelPathOrData.of(model.toString()).calculateHash();

        Files.copy(EXTERNAL_DATA_DIR.resolve("external_data.bin"), directory.resolve("external_data.bin"));
        assertNotEquals(missingHash, ModelPathOrData.of(model.toString()).calculateHash());
    }

    @Test
    void hash_of_model_without_external_data_is_the_hash_of_its_contents(@TempDir Path directory) throws IOException {
        var model = Files.writeString(directory.resolve("model.onnx"), "model contents");
        assertEquals(ModelPathOrData.of("model contents".getBytes()).calculateHash(),
                     ModelPathOrData.of(model.toString()).calculateHash());

        // The parser skips the embedded weights, which must also be hashed
        var onnxModel = Path.of("src/test/models/onnx/transformer/dummy_transformer.onnx");
        assertEquals(ModelPathOrData.of(Files.readAllBytes(onnxModel)).calculateHash(),
                     ModelPathOrData.of(onnxModel.toString()).calculateHash());
    }
}
