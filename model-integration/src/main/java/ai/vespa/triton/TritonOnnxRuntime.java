// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.llm.clients.TritonConfig;
import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
import ai.vespa.modelintegration.evaluator.OnnxRuntime;
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
    private final TritonOnnxClient client;

    // Test constructor
    public TritonOnnxRuntime() {
        this(new TritonConfig.Builder().build());
    }

    @Inject
    public TritonOnnxRuntime(TritonConfig config) {
        this.config = config;
        this.client = new TritonOnnxClient(config);
    }

    @Override
    public OnnxEvaluator evaluatorOf(String modelPath) {
        var isExplicitControlMode = config.modelControlMode() == TritonConfig.ModelControlMode.EXPLICIT;
        if (isExplicitControlMode) copyModelToRepository(modelPath);
        return new TritonOnnxEvaluator(client, modelName(modelPath), isExplicitControlMode);
    }

    @Override
    public OnnxEvaluator evaluatorOf(String modelPath, OnnxEvaluatorOptions options) {
        return evaluatorOf(modelPath); // TODO: pass options
    }

    @Override
    public void deconstruct() {
        client.close();
    }

    /** Copies the model file to the model repository */
    private void copyModelToRepository(String externalModelPath) {
        var modelRepository = Defaults.getDefaults().underVespaHome(config.modelRepositoryPath());
        var repositoryModelRoot = Paths.get(modelRepository, modelName(externalModelPath), "1");
        var repositoryModelFile = repositoryModelRoot.resolve("model.onnx");
        try {
            Files.createDirectories(repositoryModelRoot);
            Files.copy(Paths.get(externalModelPath), repositoryModelFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to copy model file to repository", e);
        }
    }

    // Hackish name to deduce model name from path.
    // It should ideally include a suffix based on the model's hash/timestamp/file size to avoid conflicts
    private static String modelName(String modelPath) {
        var name = modelPath.substring(modelPath.lastIndexOf('/') + 1);
        return name.substring(0, name.lastIndexOf('.'));
    }
}
