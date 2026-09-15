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

    // Two instances overlap GPU execution with preparation of the next request on the host.
    private static final int DEFAULT_NUM_GPU_MODEL_INSTANCES = 2;

    private final TritonOnnxClient tritonClient;
    private final boolean isModelControlExplicit;
    private final boolean shareSessionBetweenInstances;
    private final boolean gpuAvailable;
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
        this.tritonClient = tritonClient;
        this.gpuAvailable = config.gpuAvailable();

        isModelControlExplicit = config.modelControlMode() == TritonConfig.ModelControlMode.EXPLICIT;
        shareSessionBetweenInstances = config.shareOnnxSessionBetweenInstances();
        log.info("Creating Triton ONNX runtime with session sharing between model instances "
                 + (shareSessionBetweenInstances ? "enabled" : "disabled"));
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
        var resolvedOptions = resolveOptions(options, shareSessionBetweenInstances, gpuAvailable);
        var modelName = generateModelName(modelPath, resolvedOptions, shareSessionBetweenInstances, gpuAvailable);
        var modelReference = referenceModel(modelName, modelPath, resolvedOptions);

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

    // Resolves the number of model instances once. The model name and the model config are both derived
    // from the resolved options, so they always agree.
    static OnnxEvaluatorOptions resolveOptions(OnnxEvaluatorOptions options, boolean shareSession,
                                               boolean gpuAvailable) {
        if (options.numModelInstances().isPresent()) return options;
        return options.withNumModelInstances(defaultNumModelInstances(options, shareSession, gpuAvailable));
    }

    // Takes resolved options, see resolveOptions().
    static String generateModelName(String modelPath, OnnxEvaluatorOptions options, boolean shareSession,
                                    boolean gpuAvailable) {
        if (options.numModelInstances().isEmpty()) {
            throw new IllegalArgumentException("The number of model instances must be resolved before naming the model");
        }
        var fileName = Paths.get(modelPath).getFileName().toString();
        var baseName = fileName.substring(0, fileName.lastIndexOf('.')); // remove file extension
        var modelHash = ModelPathOrData.of(modelPath).calculateHash();
        var optionsHash = options.calculateHash();
        var shareSessionHash = shareSession ? 1 : 0;
        // The instance group differs between a GPU and an unpinned device, so the name must differ too.
        var deviceHash = runsOnGpu(options, gpuAvailable) ? 2 : 0;
        var combinedHash = Long.toHexString(31 * modelHash + optionsHash + shareSessionHash + deviceHash);
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

    private String createModelConfig(String modelName, OnnxEvaluatorOptions options) {
        return options.modelConfigOverride()
                .map(path -> createModelConfigFromFile(path, modelName))
                .orElseGet(() -> createModelConfigFromOptions(modelName, options))
                .toString();
    }

    // Takes resolved options, see resolveOptions().
    private String createModelConfigFromOptions(String modelName, OnnxEvaluatorOptions options) {
        // Each model instance in Triton executes requests sequentially. Concurrency comes from the number of instances.
        // This differs from EmbeddedOnnxRuntime, where one session handles all requests.
        // With a shared session, the thread counts match EmbeddedOnnxRuntime.createSessionOptions().
        // With separate sessions, the CPU cores are divided between the instances as intra-op threads.
        var executionModeValue = options.isParallel() ? "1" : "0";
        var numModelInstances = options.numModelInstances().orElseThrow();
        var intraOpThreadCountValue = Integer.toString(shareSessionBetweenInstances
                ? options.intraOpThreads()
                : intraOpThreadsForSeparateSessions(options, numModelInstances));
        var interOpThreadCountValue = Integer.toString(shareSessionBetweenInstances
                ? options.effectiveInterOpThreads()
                : options.interOpThreads());

        var configBuilder = ModelConfigOuterClass.ModelConfig.newBuilder()
                .setName(modelName)
                .addInstanceGroup(createInstanceGroup(options, numModelInstances, gpuAvailable))
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
                                .setStringValue(interOpThreadCountValue)
                                .build());

        addSessionSharingParameter(configBuilder);

        if (options.batchingMaxSize() > 1) {
            configBuilder.setDynamicBatching(ModelConfigOuterClass.ModelDynamicBatching.newBuilder().build());
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

    // Instances with separate sessions each hold a copy of the model, so there is one instance by default.
    // Instances sharing a session share its thread pool. Each instance adds one calling thread.
    // One instance per CPU core keeps the cores busy under load.
    static int defaultNumModelInstances(OnnxEvaluatorOptions options, boolean shareSession, boolean gpuAvailable) {
        if (!shareSession) {
            return 1;
        }
        return runsOnGpu(options, gpuAvailable)
                ? DEFAULT_NUM_GPU_MODEL_INSTANCES
                : Math.max(1, options.availableProcessors());
    }

    // Instances with separate sessions each have their own thread pool, so the CPU cores are divided between them.
    // Rounding up is used because it is better to overutilize CPU than to underutilize.
    static int intraOpThreadsForSeparateSessions(OnnxEvaluatorOptions options, int numModelInstances) {
        return Math.max(1, (int) Math.ceil(1d * options.availableProcessors() / numModelInstances));
    }

    // A requested GPU is required when the node has GPUs,
    // so a broken GPU setup fails instead of silently running on CPU.
    // Otherwise Triton tries the GPU and falls back to CPU.
    // This is similar to EmbeddedOnnxRuntime trying CUDA first.
    static ModelConfigOuterClass.ModelInstanceGroup.Kind deviceKind(OnnxEvaluatorOptions options,
                                                                    boolean gpuAvailable) {
        if (!options.requestingGpu()) {
            return ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_CPU; // Required is ignored without a device
        }
        if (options.gpuDeviceRequired() || gpuAvailable) {
            return ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_GPU;
        }
        return ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_AUTO;
    }

    private static boolean runsOnGpu(OnnxEvaluatorOptions options, boolean gpuAvailable) {
        return deviceKind(options, gpuAvailable) == ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_GPU;
    }

    static ModelConfigOuterClass.ModelInstanceGroup createInstanceGroup(OnnxEvaluatorOptions options,
                                                                        int numModelInstances,
                                                                        boolean gpuAvailable) {
        var kind = deviceKind(options, gpuAvailable);
        var group = ModelConfigOuterClass.ModelInstanceGroup.newBuilder()
                .setCount(numModelInstances)
                .setKind(kind);
        // Triton converts KIND_AUTO to KIND_CPU without clearing gpus, then rejects the group.
        // Leave AUTO unpinned to preserve CPU fallback when GPU availability is unknown.
        if (kind == ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_GPU) {
            group.addGpus(options.gpuDeviceNumber());
        }
        return group.build();
    }

    // Sharing one session per device loads the model weights once per device instead of once per instance.
    // The parameter requires a patched Triton ONNX backend.
    private void addSessionSharingParameter(ModelConfigOuterClass.ModelConfig.Builder configBuilder) {
        if (!shareSessionBetweenInstances) {
            return;
        }

        configBuilder.putParameters(
                "share_session_between_instances",
                ModelConfigOuterClass.ModelParameter.newBuilder()
                        .setStringValue("true")
                        .build());
    }

    private String createModelConfigFromFile(Path configPath, String modelName) {
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
        var configBuilder = config.toBuilder().setName(modelName);
        addSessionSharingParameter(configBuilder);
        return configBuilder.build().toString();
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
