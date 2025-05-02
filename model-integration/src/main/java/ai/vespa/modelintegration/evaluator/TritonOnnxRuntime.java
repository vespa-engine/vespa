// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.evaluator;

import ai.vespa.llm.clients.TritonConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.vespa.defaults.Defaults;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Experimental Triton ONNX runtime.
 *
 * @author bjorncs
 */
public class TritonOnnxRuntime extends AbstractComponent implements OnnxRuntime {

    private final TritonConfig config;

    // Test constructor
    public TritonOnnxRuntime() {
        this(new TritonConfig.Builder().build());
    }

    @Inject
    public TritonOnnxRuntime(TritonConfig config) {
        this.config = config;
    }

    @Override
    public OnnxEvaluator evaluatorOf(String modelPath) {
        return new TritonOnnxEvaluator(config, copyFileToRepositoryAndGetModelId(modelPath));
    }

    @Override
    public OnnxEvaluator evaluatorOf(String modelPath, OnnxEvaluatorOptions options) {
        return evaluatorOf(modelPath); // TODO: pass options
    }

    /** Copies the model file to the model repository and returns the model id */
    private static String copyFileToRepositoryAndGetModelId(String externalModelPath) {
        var modelRepository = Defaults.getDefaults().underVespaHome("var/triton/model-repository");
        var modelName = externalModelPath.substring(externalModelPath.lastIndexOf('/') + 1);
        modelName = modelName.substring(0, modelName.lastIndexOf('.'));

        var repositoryModelRoot = Paths.get(modelRepository, modelName, "1");
        var repositoryModelFile = repositoryModelRoot.resolve("model.onnx");
        try {
            Files.createDirectories(repositoryModelRoot);
            Files.copy(Paths.get(externalModelPath), repositoryModelFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to copy model file to repository", e);
        }
        return modelName;
    }

}
