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
import com.yahoo.container.protect.ProcessTerminator;
import com.yahoo.io.IOUtils;
import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.text.Text;
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
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ONNX Runtime implementation that uses Triton Inference Server for model inference.
 *
 * @author bjorncs
 * @author glebashnik
 */
public class TritonOnnxRuntime extends AbstractComponent implements OnnxRuntime {
    private static final Logger log = Logger.getLogger(TritonOnnxRuntime.class.getName());

    private final TritonConfig config;
    private final TritonOnnxClient tritonClient;
    private final boolean isModelControlExplicit;
    private final Path modelRepositoryPath;
    private final ProcessTerminator processTerminator;

    // The key is a model name containing hash of model content and options
    private final ConcurrentMap<String, TritonModelResource> modelResources = new ConcurrentHashMap<>();

    // Represents a model in Triton repository with reference counting.
    // When created it copies model files to Triton repository and loads the model.
    // When no references remain, the model is unloaded and model files are deleted.
    class TritonModelResource extends AbstractResource {
        public final String modelName;

        private TritonModelResource(String modelName, String modelPath, OnnxEvaluatorOptions options) {
            this.modelName = modelName;

            if (isModelControlExplicit) {
                var modelConfig = createModelConfig(modelName, options);
                copyModelFilesToModelRepository(modelName, modelPath, modelConfig);
                tritonClient.loadUntilModelReady(modelName);
            }
        }

        @Override
        public void destroy() {
            modelResources.computeIfPresent(modelName, (key, value) -> {
                if (isModelControlExplicit) {
                    tritonClient.unloadUntilModelNotReady(modelName);
                    deleteModelFilesFromModelRepository(modelName);
                }
                return null; // remove from map
            });
        }
    }

    public static TritonOnnxRuntime createTestInstance() {
        return new TritonOnnxRuntime(new TritonConfig.Builder().build());
    }

    @Inject
    public TritonOnnxRuntime(TritonConfig config) {
        this(config, new ProcessTerminator());
    }

    // Injectable ProcessTerminator for testing.
    TritonOnnxRuntime(TritonConfig config, ProcessTerminator processTerminator) {
        this(config, new TritonOnnxClient(config), processTerminator);
    }

    // Injectable tritonClient and ProcessTerminator for testing.
    TritonOnnxRuntime(TritonConfig config, TritonOnnxClient tritonClient, ProcessTerminator processTerminator) {
        log.info("Creating Triton ONNX runtime");
        
        this.config = config;
        this.tritonClient = tritonClient;
        this.processTerminator = processTerminator;

        isModelControlExplicit = config.modelControlMode() == TritonConfig.ModelControlMode.EXPLICIT;
        modelRepositoryPath = Path.of(Defaults.getDefaults().underVespaHome(config.modelRepositoryPath()));

        if (isModelControlExplicit) {
            try {
                if (tritonClient.isHealthy()) {
                    tritonClient.unloadAllModels();
                }
            } catch (TritonOnnxClient.TritonException e) {
                // Ignore this since Triton might not be needed because there are no ONNX models in the app.
            }
            
            deleteAllModelFilesFromModelRepository();
        }
    }

    @Override
    public OnnxEvaluator evaluatorOf(String modelPath, OnnxEvaluatorOptions options) {
        // Needs a healthy Triton server to load a model.
        try {
            var isTritonHealthy = tritonClient.isHealthy();
            
            if (!isTritonHealthy) {
                processTerminator.logAndDie(Text.format("Die because Triton server is not healthy at %s", config.target()));
            }
        } catch (TritonOnnxClient.TritonException e) {
            processTerminator.logAndDie(Text.format("Die because Triton server can't be reached at %s", config.target()));
        }

        var modelName = generateModelName(modelPath, options);
        var modelReferenceHolder = new ResourceReference[1];

        // Key synchronized create, reference, release and put model resource avoids concurrency issues.
        modelResources.compute(modelName, (key, existingModelResource) -> {
            if (existingModelResource != null) {
                modelReferenceHolder[0] = existingModelResource.refer();
                return existingModelResource;
            }

            var newModelResource = new TritonModelResource(modelName, modelPath, options);
            modelReferenceHolder[0] = newModelResource.refer();
            newModelResource.release();
            return newModelResource;
        });

        return new TritonOnnxEvaluator(modelName, modelReferenceHolder[0], tritonClient, isModelControlExplicit);
    }

    static String generateModelName(String modelPath, OnnxEvaluatorOptions options) {
        var fileName = Paths.get(modelPath).getFileName().toString();
        var baseName = fileName.substring(0, fileName.lastIndexOf('.')); // remove file extension
        var modelHash = ModelPathOrData.of(modelPath).calculateHash();
        var optionsHash = options.calculateHash();
        var combinedHash = Long.toHexString(31 * modelHash + optionsHash);
        return baseName + "_" + combinedHash; // add hash to avoid conflicts
    }

    private Path getModelDirInModelRepository(String modelName) {
        return modelRepositoryPath.resolve(modelName);
    }

    /**
     * Copies the model file and config to a model repository directory that Triton has access to.
     */
    private void copyModelFilesToModelRepository(String modelName, String externalModelPath, String modelConfig) {
        var modelDirPath = getModelDirInModelRepository(modelName);
        var modelVersionPath = modelDirPath.resolve("1");
        var modelFilePath = modelVersionPath.resolve("model.onnx");
        var modelConfigPath = modelDirPath.resolve("config.pbtxt");

        try {
            // Create directory for model name and version with correct permissions
            Files.createDirectories(
                    modelVersionPath,
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxrwxr-x")));

            Files.copy(Paths.get(externalModelPath), modelFilePath, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(modelConfigPath, modelConfig);

            // Explicitly grant world read to ensure that Triton can read model files
            addReadPermissions(modelFilePath);
            addReadPermissions(modelConfigPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to copy model file to repository", e);
        }
    }

    private void deleteModelFilesFromModelRepository(String modelName) {
        var modelDir = getModelDirInModelRepository(modelName);
        IOUtils.recursiveDeleteDir(modelDir.toFile());
    }

    private static void addReadPermissions(Path path) throws IOException {
        var modelPerms = Files.getPosixFilePermissions(path);
        modelPerms.add(PosixFilePermission.GROUP_READ);
        modelPerms.add(PosixFilePermission.OTHERS_READ);
        Files.setPosixFilePermissions(path, modelPerms);
    }

    private static String createModelConfig(String modelName, OnnxEvaluatorOptions options) {
        return options.modelConfigOverride()
                .map(path -> createModelConfigFromFile(path, modelName))
                .orElseGet(() -> createModelConfigFromOptions(modelName, options))
                .toString();
    }

    private static String createModelConfigFromOptions(String modelName, OnnxEvaluatorOptions options) {
        // Similar to EmbeddedOnnxRuntime.overrideOptions(), relies on Triton to fall back to CPU if GPU is
        // not available.
        var deviceKind = options.gpuDeviceRequired()
                ? ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_GPU
                : (options.gpuDeviceNumber() >= 0)
                        ? ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_AUTO
                        : ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_CPU;

        // Each model instance in Triton executes requests sequentially, which is different from EmbeddedOnnxRuntime
        // where one model instance (session) handles all requests.
        // To maximize CPU utilization with Triton, available cores are ca. divided between model instances,
        // Rounding up is used because it is better to overutilize CPU than to underutilize.
        // Intra-op threads parallelize execution of each operator improving performance for any model.
        var intraOpThreadCountValue = Integer.toString(
                Math.max(1, (int) Math.ceil(1d * options.availableProcessors() / options.numModelInstances())));

        var executionModeValue = options.executionMode() == OnnxEvaluatorOptions.ExecutionMode.PARALLEL ? "1" : "0";

        var configBuilder = ModelConfigOuterClass.ModelConfig.newBuilder()
                .setName(modelName)
                .addInstanceGroup(ModelConfigOuterClass.ModelInstanceGroup.newBuilder()
                        .setCount(options.numModelInstances())
                        .setKind(deviceKind)
                        .build())
                .setPlatform("onnxruntime_onnx")
                .setMaxBatchSize(options.batchingMaxSize())
                .putParameters(
                        "execution_mode",
                        ModelConfigOuterClass.ModelParameter.newBuilder()
                                .setStringValue(executionModeValue)
                                .build())
                .putParameters(
                        "enable_mem_arena",
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
                                .setStringValue(intraOpThreadCountValue)
                                .build())
                .putParameters(
                        "inter_op_thread_count",
                        ModelConfigOuterClass.ModelParameter.newBuilder()
                                .setStringValue(Integer.toString(options.interOpThreads()))
                                .build());

        if (options.batchingMaxSize() > 1) {
            var dynamicBatchingBuilder = ModelConfigOuterClass.ModelDynamicBatching.newBuilder();
            options.batchingMaxDelay()
                    .ifPresent(delay -> dynamicBatchingBuilder.setMaxQueueDelayMicroseconds(delay.toMillis() * 1000L));
            configBuilder.setDynamicBatching(dynamicBatchingBuilder.build());
        }

        return configBuilder.build().toString();
    }

    private static String createModelConfigFromFile(Path configPath, String modelName) {
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

        // Replaces model name with the one that includes model content and options hash to avoid conflicts.
        return config.toBuilder().setName(modelName).build().toString();
    }

    private void deleteAllModelFilesFromModelRepository() {
        if (!Files.exists(modelRepositoryPath)) {
            return;
        }

        try (var stream = Files.list(modelRepositoryPath)) {
            stream.forEach(path -> {
                log.warning(() -> "Deleting leftover model files from Triton model repository: " + path);

                if (!IOUtils.recursiveDeleteDir(path.toFile())) {
                    log.warning(() -> "Failed to delete model files from Triton model repository: {}" + path);
                }
            });
        } catch (IOException e) {
            log.log(Level.SEVERE, e, () -> "Failed to list files in Triton model repository: " + modelRepositoryPath);
        }
    }

    @Override
    public void deconstruct() {
        modelResources.values().forEach(TritonModelResource::destroy);
        tritonClient.close();
    }
}
