// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.llm.clients.TritonConfig;
import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.vespa.defaults.Defaults;
import inference.ModelConfigOuterClass;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Experimental Triton ONNX runtime.
 *
 * @author bjorncs
 * @author glebashnik
 */
public class TritonOnnxRuntime extends AbstractComponent implements OnnxRuntime {

    private final TritonConfig config;
    private final TritonOnnxClient client;
    private final boolean isExplicitControlMode;
    private final TritonOnnxModelLoader modelLoader;

    // Test constructor
    public TritonOnnxRuntime() {
        this(new TritonConfig.Builder().build());
    }

    @Inject
    public TritonOnnxRuntime(TritonConfig config) {
        this.config = config;
        this.client = new TritonOnnxClient(config);
        this.isExplicitControlMode = config.modelControlMode() == TritonConfig.ModelControlMode.EXPLICIT;
        this.modelLoader = new TritonOnnxModelLoader(client, isExplicitControlMode);
    }

    @Override
    public OnnxEvaluator evaluatorOf(String modelPath, OnnxEvaluatorOptions options) {
        if (!client.isHealthy()) {
            throw new IllegalStateException("Triton server is not healthy! (target=%s)".formatted(config.target()));
        }
        
        if (isExplicitControlMode) {
            copyModelToRepository(modelPath, options);
        }

        return new TritonOnnxEvaluator(client, modelName(modelPath), modelLoader);
    }

    @Override
    public void deconstruct() {
        modelLoader.unloadAllModels();
        client.close();
    }

    /** Copies the model file to the model repository and serializes the config */
    private void copyModelToRepository(String externalModelPath, OnnxEvaluatorOptions options) {
        var modelRepositoryPath = Defaults.getDefaults().underVespaHome(config.modelRepositoryPath());
        var modelBasePath = Paths.get(modelRepositoryPath, modelName(externalModelPath));
        
        var modelVersionPath = modelBasePath.resolve("1");
        var modelFilePath = modelVersionPath.resolve("model.onnx");
        var modelConfigPath = modelBasePath.resolve("config.pbtxt");
        
        try {
            // Create directory for model name and version with correct permissions
            Files.createDirectories(
                    modelVersionPath,
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxrwxr-x")));

            Files.copy(Paths.get(externalModelPath), modelFilePath, StandardCopyOption.REPLACE_EXISTING);
            var modelConfig = options.rawConfig()
                    .orElseGet(() -> generateConfigFromEvaluatorOptions(externalModelPath, options).toString());
            Files.writeString(modelConfigPath, modelConfig);

            // To ensure that the Triton can read the model files, explicitly grant world read
            addReadPermissions(modelFilePath);
            addReadPermissions(modelConfigPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to copy model file to repository", e);
        }
    }

    private static void addReadPermissions(Path path) throws IOException {
        var modelPerms = Files.getPosixFilePermissions(path);
        modelPerms.add(PosixFilePermission.GROUP_READ);
        modelPerms.add(PosixFilePermission.OTHERS_READ);
        Files.setPosixFilePermissions(path, modelPerms);
    }

    // Hackish name to deduce model name from path.
    // It should ideally include a suffix based on the model's hash/timestamp/file size to avoid conflicts
    static String modelName(String modelPath) {
        var name = modelPath.substring(modelPath.lastIndexOf('/') + 1);
        return name.substring(0, name.lastIndexOf('.'));
    }

    // Generate a default model config based on evaluator options.
    // These are not necqessarily optimal but should closely match the effective configuration for the embedded ONNX runtime.
    private static ModelConfigOuterClass.ModelConfig generateConfigFromEvaluatorOptions(
            String modelPaths, OnnxEvaluatorOptions options) {
        // Similar to EmbeddedOnnxRuntime.overrideOptions(), relies on Triton to fall back to CPU if GPU is not available.
        var kind = options.gpuDeviceRequired()
                ? ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_GPU
                : (options.gpuDeviceNumber() >= 0)
                        ? ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_AUTO
                        : ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_CPU;
        return ModelConfigOuterClass.ModelConfig.newBuilder()
                .setName(modelName(modelPaths))
                .addInstanceGroup(ModelConfigOuterClass.ModelInstanceGroup.newBuilder()
                        .setCount(1)
                        .setKind(kind)
                        .build())
                .setPlatform("onnxruntime_onnx")
                .setMaxBatchSize(1) // No batching for now.
                .putParameters("enable_mem_area", ModelConfigOuterClass.ModelParameter.newBuilder()
                        .setStringValue("0")
                        .build())
                .putParameters("enable_mem_pattern", ModelConfigOuterClass.ModelParameter.newBuilder()
                        .setStringValue("0")
                        .build())
                .putParameters("intra_op_thread_count", ModelConfigOuterClass.ModelParameter.newBuilder()
                        .setStringValue(Integer.toString(options.intraOpThreads()))
                        .build())
                .putParameters("inter_op_thread_count", ModelConfigOuterClass.ModelParameter.newBuilder()
                        .setStringValue(Integer.toString(options.interOpThreads()))
                        .build())
                .build();
    }
}
