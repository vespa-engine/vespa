// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.llm.clients.TritonConfig;
import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import ai.vespa.modelintegration.utils.ModelPathOrData;
import com.google.protobuf.TextFormat;
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
 * ONNX Runtime implementation that uses Triton Inference Server for model inference.
 *
 * @author bjorncs
 * @author glebashnik
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
    public OnnxEvaluator evaluatorOf(String modelPath, OnnxEvaluatorOptions options) {
        if (!client.isHealthy()) {
            throw new IllegalStateException("Triton server is not healthy! (target=%s)".formatted(config.target()));
        }

        var modelName = createModelName(modelPath, options);
        var isExplicitControlMode = config.modelControlMode() == TritonConfig.ModelControlMode.EXPLICIT;

        if (isExplicitControlMode) {
            copyModelToRepository(modelName, modelPath, options);
        }

        return new TritonOnnxEvaluator(client, modelName, isExplicitControlMode);
    }

    @Override
    public void deconstruct() {
        client.close();
    }

    /**
     * Copies the model file to the model repository and serializes the config
     */
    private void copyModelToRepository(String modelName, String externalModelPath, OnnxEvaluatorOptions options) {
        var modelRepositoryPath = Defaults.getDefaults().underVespaHome(config.modelRepositoryPath());
        var modelRootPath = Paths.get(modelRepositoryPath, modelName);
        var modelVersionPath = modelRootPath.resolve("1");
        var modelFilePath = modelVersionPath.resolve("model.onnx");
        var modelConfigPath = modelRootPath.resolve("config.pbtxt");

        try {
            // Create directory for model name and version with correct permissions
            Files.createDirectories(
                    modelVersionPath,
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxrwxr-x")));

            Files.copy(Paths.get(externalModelPath), modelFilePath, StandardCopyOption.REPLACE_EXISTING);

            var modelConfig = options.modelConfigOverride()
                    .map(path -> prepareModelConfigOverride(path, modelName))
                    .orElseGet(() -> generateConfigFromEvaluatorOptions(modelName, options))
                    .toString();
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

    static String createModelName(String modelPath, OnnxEvaluatorOptions options) {
        var fileName = Paths.get(modelPath).getFileName().toString();
        var baseName = fileName.substring(0, fileName.lastIndexOf('.')); // remove file extension
        var modelHash = ModelPathOrData.of(modelPath).calculateHash();
        var optionsHash = options.calculateHash();
        var combinedHash = Long.toHexString(31 * modelHash + optionsHash);
        return baseName + "_" + combinedHash; // add hash to avoid conflicts
    }

    private ModelConfigOuterClass.ModelConfig prepareModelConfigOverride(Path configPath, String modelName) {
        String configStr;

        try {
            configStr = Files.readString(configPath);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read model config override file: " + configPath, e);
        }

        ModelConfigOuterClass.ModelConfig config;

        try {
            config = TextFormat.parse(configStr, ModelConfigOuterClass.ModelConfig.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse model config override:\n" + configStr, e);
        }

        // Replace model name with the generated name.
        // Makes overridden model names consistent with generated ones and avoids conflicts.
        return config.toBuilder().setName(modelName).build();
    }

    private static ModelConfigOuterClass.ModelConfig generateConfigFromEvaluatorOptions(
            String modelName, OnnxEvaluatorOptions options) {
        // Similar to EmbeddedOnnxRuntime.overrideOptions(), relies on Triton to fall back to CPU if GPU is not
        // available.
        var deviceKind = options.gpuDeviceRequired()
                ? ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_GPU
                : (options.gpuDeviceNumber() >= 0)
                        ? ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_AUTO
                        : ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_CPU;

        var configBuilder = ModelConfigOuterClass.ModelConfig.newBuilder()
                .setName(modelName)
                .addInstanceGroup(ModelConfigOuterClass.ModelInstanceGroup.newBuilder()
                        .setCount(options.numModelInstances())
                        .setKind(deviceKind)
                        .build())
                .setPlatform("onnxruntime_onnx")
                .setMaxBatchSize(options.batchingMaxSize())
                .putParameters(
                        "enable_mem_area",
                        ModelConfigOuterClass.ModelParameter.newBuilder()
                                .setStringValue("0")
                                .build())
                .putParameters(
                        "enable_mem_pattern",
                        ModelConfigOuterClass.ModelParameter.newBuilder()
                                .setStringValue("0")
                                .build())
                .putParameters(
                        "intra_op_thread_count",
                        ModelConfigOuterClass.ModelParameter.newBuilder()
                                .setStringValue(Integer.toString(options.intraOpThreads()))
                                .build())
                .putParameters(
                        "inter_op_thread_count",
                        ModelConfigOuterClass.ModelParameter.newBuilder()
                                .setStringValue(Integer.toString(options.interOpThreads()))
                                .build());

        // Add dynamic batching if batch size is greater than 1
        if (options.batchingMaxSize() > 1) {
            var dynamicBatching = ModelConfigOuterClass.ModelDynamicBatching.newBuilder()
                    .setMaxQueueDelayMicroseconds(options.batchingMaxDelay().toMillis() * 1000L)
                    .build();
            configBuilder.setDynamicBatching(dynamicBatching);
        }

        return configBuilder.build();
    }
}
