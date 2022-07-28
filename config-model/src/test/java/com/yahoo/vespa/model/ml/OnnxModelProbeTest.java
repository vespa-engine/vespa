// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.ml;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OnnxModelProbeTest {

    @Test
    void testProbedOutputTypes() throws IOException {

        Path appDir = Path.fromString("src/test/cfg/application/onnx_probe");
        Path storedAppDir = appDir.append("copy");
        try {
            FilesApplicationPackage app = FilesApplicationPackage.fromFile(appDir.toFile());
            Path modelPath = Path.fromString("files/dynamic_model.onnx");
            String output = "out";
            Map<String, TensorType> inputTypes = Map.of(
                    "input1", TensorType.fromSpec("tensor<float>(d0[1],d1[2])"),
                    "input2", TensorType.fromSpec("tensor<float>(d0[1],d1[2])"));
            TensorType expected = TensorType.fromSpec("tensor<float>(d0[1],d1[2],d2[2])");

            TensorType outputType = OnnxModelProbe.probeModel(app, modelPath, output, inputTypes);

            // if 'vespa-analyze-onnx-model' was unavailable, specifically cache expected type
            if (outputType.equals(TensorType.empty)) {
                OnnxModelProbe.writeProbedOutputType(app, modelPath, output, inputTypes, expected);
            } else {
                assertEquals(outputType, expected);
            }

            // Test loading from generated info
            storedAppDir.toFile().mkdirs();
            IOUtils.copyDirectory(appDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile(),
                    storedAppDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
            app = FilesApplicationPackage.fromFile(storedAppDir.toFile());
            outputType = OnnxModelProbe.probeModel(app, modelPath, output, inputTypes);
            assertEquals(outputType, expected);

        } finally {
            IOUtils.recursiveDeleteDir(appDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
            IOUtils.recursiveDeleteDir(storedAppDir.toFile());
        }
    }

}
