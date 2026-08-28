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
import com.yahoo.io.IOUtils;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.language.process.TimeoutException;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.yolean.Exceptions;
import inference.ModelConfigOuterClass;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ONNX Runtime implementation that uses Triton Inference Server for model inference.
 * It owns gRPC client, responsible for managing model repository and the reference-counted models loaded in Triton.
 * There should be only one instance of TritonOnnxRuntime component, enforced by
 * RestartOnDeployForTritonOnnxRuntimeValidator.
 *
 * @author bjorncs
 * @author glebashnik
 */
public class TritonOnnxRuntime extends AbstractComponent implements OnnxRuntime {
    private static final Logger log = Logger.getLogger(TritonOnnxRuntime.class.getName());

    private final TritonOnnxClient tritonClient;
    private final boolean isModelControlExplicit;
    private final Path modelRepositoryPath;

    // The key is a model name containing hash of model content and options.
    // The map's per-key compute is the only synchronization of model bookkeeping:
    // it guards the reference counts and serializes load and cleanup of the same model.
    private final ConcurrentMap<String, TritonModelResource> modelResources = new ConcurrentHashMap<>();

    // A model loaded in Triton, reference counted by the evaluators using it.
    // When the count reaches zero, the model is unloaded and its files are deleted.
    // Not using AbstractResource because it has its own synchronization mechanism that can create a deadlock
    // when combined with synchronization through modelResources.
    private static class TritonModelResource {
        int referenceCount = 0;
    }

    @Inject
    public TritonOnnxRuntime(TritonConfig config) {
        this(config, new TritonOnnxClient(config));
    }

    // Injectable tritonClient for testing.
    // Takes ownership of the client: deconstruct() closes it.
    TritonOnnxRuntime(TritonConfig config, TritonOnnxClient tritonClient) {
        log.info("Creating Triton ONNX runtime");

        this.tritonClient = tritonClient;

        isModelControlExplicit = config.modelControlMode() == TritonConfig.ModelControlMode.EXPLICIT;
        modelRepositoryPath = Path.of(Defaults.getDefaults().underVespaHome(config.modelRepositoryPath()));

        if (isModelControlExplicit) {
            cleanUpLeftovers();
        }
    }

    public static TritonOnnxRuntime createTestInstance() {
        return new TritonOnnxRuntime(new TritonConfig.Builder().build());
    }

    @Override
    public OnnxEvaluator evaluatorOf(String modelPath, OnnxEvaluatorOptions options) {
        var modelName = generateModelName(modelPath, options);
        var modelReference = referenceModel(modelName, modelPath, options);

        try {
            return new TritonOnnxEvaluator(modelName, modelReference, tritonClient, isModelControlExplicit);
        } catch (RuntimeException e) {
            modelReference.close(); // Don't leak the model when creating the evaluator fails.
            throw e;
        }
    }

    // Returns a reference to the model, first copying and loading it into Triton if needed.
    // The map's per-key compute serializes this with a concurrent release of the same model.
    private ResourceReference referenceModel(String modelName, String modelPath, OnnxEvaluatorOptions options) {
        modelResources.compute(modelName, (key, modelResource) -> {
            ensureModelReady(modelName, modelPath, options);

            if (modelResource == null) {
                modelResource = new TritonModelResource();
            }

            modelResource.referenceCount++;
            return modelResource;
        });

        // Each reference releases the model exactly once.
        var closed = new AtomicBoolean(false);

        return () -> {
            if (closed.getAndSet(true)) {
                throw new IllegalStateException("The reference to model " + modelName + " is already closed");
            }

            releaseModel(modelName);
        };
    }

    private void releaseModel(String modelName) {
        modelResources.compute(modelName, (key, modelResource) -> {
            // Already removed, e.g. force-released by deconstruct().
            if (modelResource == null) {
                return null;
            }

            modelResource.referenceCount--;

            if (modelResource.referenceCount > 0) {
                return modelResource;
            }

            if (isModelControlExplicit) {
                cleanUpModel(modelName);
            }

            return null; // remove from map
        });
    }

    // Uses an already-ready model, or copies the model into the repository and loads it into Triton.
    // A load failure only fails the reconfiguration requesting this model; cleanup is best effort.
    private void ensureModelReady(String modelName, String modelPath, OnnxEvaluatorOptions options) {
        if (!isModelControlExplicit) {
            return;
        }

        try {
            if (tritonClient.isModelReady(modelName)) {
                return; // Adopt a model that Triton already has ready
            }
        } catch (TritonOnnxClient.TritonException | TimeoutException e) {
            // Still attempt to install and load the requested model below.
        }

        try {
            var modelConfig = createModelConfig(modelName, options);
            copyModelFilesToModelRepository(modelName, modelPath, modelConfig);
            tritonClient.loadUntilModelReady(modelName);
        } catch (RuntimeException e) {
            // A failed load must not leave the model in Triton or its files in the repository.
            cleanUpModel(modelName);
            throw e;
        }
    }

    // Best-effort unloading of a model from Triton and removing its files from the model repository.
    private void cleanUpModel(String modelName) {
        try {
            tritonClient.unloadUntilModelNotReady(modelName);
        } catch (RuntimeException e) {
            // Keep the files if unload fails: deleting them while the model may still be loaded would break it.
            log.log(Level.WARNING, e, () -> "Failed to unload model " + modelName + ", keeping its files");
            return;
        }

        deleteModelFilesFromModelRepository(modelName);
    }

    // Best-effort cleanup of models left by a previous process.
    private void cleanUpLeftovers() {
        try {
            tritonClient.unloadAllModels();
            deleteAllModelFilesFromModelRepository();
        } catch (RuntimeException e) {
            // Triton may be down or not needed at all (no ONNX models in the app).
            log.log(Level.WARNING, () -> "Skipped cleaning up leftover models: " + Exceptions.toMessageString(e));
        }
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
        deleteModelDir(getModelDirInModelRepository(modelName));
    }

    private static void deleteModelDir(Path modelDir) {
        if (Files.exists(modelDir) && !IOUtils.recursiveDeleteDir(modelDir.toFile())) {
            log.warning(() -> "Failed to delete model files from Triton model repository: " + modelDir);
        }
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

        // Triton's ONNX Runtime backend enables all optimizations when the graph optimization level is unspecified,
        // and maps level 2 to disabling all optimizations (ORT_DISABLE_ALL).
        if (!options.optimizeModel()) {
            configBuilder.setOptimization(ModelConfigOuterClass.ModelOptimizationPolicy.newBuilder()
                    .setGraph(ModelConfigOuterClass.ModelOptimizationPolicy.Graph.newBuilder()
                            .setLevel(2)
                            .build())
                    .build());
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
                deleteModelDir(path);
            });
        } catch (IOException e) {
            log.log(Level.SEVERE, e, () -> "Failed to list files in Triton model repository: " + modelRepositoryPath);
        }
    }

    @Override
    public void deconstruct() {
        for (var modelName : modelResources.keySet()) {
            modelResources.compute(modelName, (key, modelResource) -> {
                if (modelResource != null && isModelControlExplicit) {
                    cleanUpModel(modelName);
                }

                return null; // remove from map
            });
        }

        tritonClient.close();
    }
}
