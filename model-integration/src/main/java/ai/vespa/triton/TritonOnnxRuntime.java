// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.llm.clients.TritonConfig;
import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
import ai.vespa.modelintegration.evaluator.OnnxPoolingOptions;
import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import ai.vespa.modelintegration.evaluator.OnnxPadder;
import ai.vespa.modelintegration.evaluator.OnnxStreamParser;
import ai.vespa.modelintegration.utils.ModelPathOrData;
import com.google.protobuf.TextFormat;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.io.IOUtils;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.language.process.TimeoutException;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.yolean.Exceptions;
import inference.ModelConfigOuterClass.ModelConfig;
import inference.ModelConfigOuterClass.ModelDynamicBatching;
import inference.ModelConfigOuterClass.ModelInstanceGroup;
import inference.ModelConfigOuterClass.ModelInstanceGroup.Kind;
import inference.ModelConfigOuterClass.ModelOptimizationPolicy;
import inference.ModelConfigOuterClass.ModelParameter;
import net.jpountz.xxhash.XXHashFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ONNX Runtime implementation that uses Triton Inference Server for inference.
 * It is responsible for managing model repository and owns reference-counted models loaded in Triton.
 * There should be only one instance of TritonOnnxRuntime component, enforced by
 * RestartOnDeployForTritonOnnxRuntimeValidator.
 *
 * @author bjorncs
 * @author glebashnik
 * @author ingridhabrekke
 */
public class TritonOnnxRuntime extends AbstractComponent implements OnnxRuntime {
    private static final Logger log = Logger.getLogger(TritonOnnxRuntime.class.getName());
    private static final String MODEL_FILE_NAME = "model.onnx";

    // The attention mask input Triton pools with unless the config names another.
    private static final String DEFAULT_POOLING_ATTENTION_MASK = "attention_mask";

    private final TritonOnnxClient tritonClient;
    private final boolean isModelControlExplicit;
    private final boolean shareSessionBetweenInstances;
    private final boolean runtimePoolingEnabled;
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
        // Whether Triton accepts a non-zero max batch size for the model.
        // Known only for a model that this runtime loaded with a generated config.
        Optional<Boolean> supportsBatching = Optional.empty();
    }

    @Inject
    public TritonOnnxRuntime(TritonConfig config) {
        this(config, new TritonOnnxClient(config));
    }

    // Injectable tritonClient for testing.
    // Takes ownership of the client: deconstruct() closes it.
    TritonOnnxRuntime(TritonConfig config, TritonOnnxClient tritonClient) {
        this.tritonClient = tritonClient;

        isModelControlExplicit = config.modelControlMode() == TritonConfig.ModelControlMode.EXPLICIT;
        shareSessionBetweenInstances = config.shareOnnxSessionBetweenInstances();
        runtimePoolingEnabled = config.onnxPooling();
        log.info("Creating Triton ONNX runtime with session sharing between model instances "
                 + (shareSessionBetweenInstances ? "enabled" : "disabled")
                 + " and runtime pooling " + (runtimePoolingEnabled ? "enabled" : "disabled"));
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
        // Options are used as-is when this runtime does not generate the model config from them:
        // a supplied config override owns its instance configuration, and the other model control modes
        // leave loading to Triton. Likewise, the pooling the options ask for applies only to a generated config.
        var generatesConfig = isModelControlExplicit && options.modelConfigOverride().isEmpty();
        var resolvedOptions = generatesConfig ? resolveInstanceCount(options, shareSessionBetweenInstances) : options;
        var pooling = runtimePoolingEnabled && generatesConfig ? options.runtimePooling() : Optional.<OnnxPoolingOptions>empty();
        if (runtimePoolingEnabled && ! generatesConfig && options.runtimePooling().isPresent())
            log.info("Not pooling " + modelPath + " in Triton: " +
                     (options.modelConfigOverride().isPresent() ? "it has a model config override" : "the model control mode is not EXPLICIT"));
        var modelName = generateModelName(modelPath, resolvedOptions, shareSessionBetweenInstances, pooling);
        var modelReference = referenceModel(modelName, modelPath, resolvedOptions, pooling);

        try {
            OnnxEvaluator evaluator = new TritonOnnxEvaluator(modelName, modelReference, tritonClient, isModelControlExplicit, pooling);

            if (options.padding().isPresent() && shouldPadForBatching(modelPath, resolvedOptions,
                    () -> supportsBatching(modelName).orElseGet(() -> declaresBatchDimension(modelPath))))
                evaluator = OnnxPadder.of(options.padding().get(), evaluator, modelName);

            return evaluator;
        } catch (RuntimeException e) {
            modelReference.close(); // Don't leak the model when creating the evaluator fails.
            throw e;
        }
    }

    /**
     * Returns whether to pad inputs, so that Triton's dynamic batcher can combine requests
     * of different lengths. Padding is useful for batching on a GPU.
     * With a model config override the batching setup is not known here, so padding is used as configured.
     * A model that does not support batching is never padded, since Triton does not batch it.
     * A dynamically quantized model is never padded, since padding changes its results.
     */
    static boolean shouldPadForBatching(String modelPath, OnnxEvaluatorOptions options, BooleanSupplier supportsBatching) {
        return (options.modelConfigOverride().isPresent()
                || (OnnxPadder.isBatchingOnGpu(options) && supportsBatching.getAsBoolean()))
               && ! OnnxPadder.isDynamicallyQuantized(modelPath);
    }

    // Returns a reference to the model, first copying and loading it into Triton if needed.
    // The map's per-key compute serializes this with a concurrent release of the same model.
    private ResourceReference referenceModel(
            String modelName, String modelPath, OnnxEvaluatorOptions options, Optional<OnnxPoolingOptions> pooling) {
        modelResources.compute(modelName, (key, modelResource) -> {
            var supportsBatching = ensureModelReady(modelName, modelPath, options, pooling);

            if (modelResource == null) {
                modelResource = new TritonModelResource();
            }
            if (supportsBatching.isPresent()) {
                modelResource.supportsBatching = supportsBatching;
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
    // Returns whether Triton supports batching for a model that it loads with a generated config.
    private Optional<Boolean> ensureModelReady(
            String modelName, String modelPath, OnnxEvaluatorOptions options, Optional<OnnxPoolingOptions> pooling) {
        if (!isModelControlExplicit) {
            return Optional.empty();
        }

        try {
            if (tritonClient.isModelReady(modelName)) {
                return Optional.empty(); // Adopt a model that Triton already has ready
            }
        } catch (TritonOnnxClient.TritonException | TimeoutException e) {
            // Still attempt to copy and load the requested model below.
        }

        // A model config override sets the batching itself, so loadModel does not use the batching argument.
        if (options.modelConfigOverride().isPresent()) {
            loadModel(modelName, modelPath, options, pooling, true);
            return Optional.empty();
        }

        var supportsBatching = declaresBatchDimension(modelPath);
        if (!supportsBatching && options.batchingMaxSize() > 1) {
            log.warning("Not batching model " + modelPath + " in Triton although batching max size is " +
                        options.batchingMaxSize() + ": an input or output of the model has no batch dimension");
        }
        try {
            loadModel(modelName, modelPath, options, pooling, supportsBatching);
            return Optional.of(supportsBatching);
        } catch (TritonOnnxClient.TritonException e) {
            if (!supportsBatching || !TritonOnnxClient.isBatchingRejection(e)) throw e;
            // OnnxStreamParser reads declared shapes, but Triton decides from the shapes ONNX Runtime infers.
            // Triton rejects batching only for a model without a batch dimension, so the model loads without batching.
            log.warning("Loading model " + modelPath + " in Triton without batching, since Triton rejected batching: " +
                        Exceptions.toMessageString(e));
            loadModel(modelName, modelPath, options, pooling, false);
            return Optional.of(false);
        }
    }

    // Callers hold a reference to the model, so its resource is in the map.
    Optional<Boolean> supportsBatching(String modelName) {
        var modelResource = modelResources.get(modelName);
        return modelResource == null ? Optional.empty() : modelResource.supportsBatching;
    }

    private void loadModel(String modelName, String modelPath, OnnxEvaluatorOptions options,
                           Optional<OnnxPoolingOptions> pooling, boolean supportsBatching) {
        try {
            var modelConfig = createModelConfig(modelName, options, pooling, supportsBatching);
            copyModelFilesToModelRepository(modelName, modelPath, modelConfig);
            tritonClient.loadUntilModelReady(modelName);
        } catch (RuntimeException e) {
            // Attempt to unload the failed model and remove its files; cleanup is best effort.
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

    static OnnxEvaluatorOptions resolveInstanceCount(OnnxEvaluatorOptions options, boolean shareSession) {
        return options.numModelInstances().isPresent()
                ? options : options.withNumModelInstances(defaultNumModelInstances(options, shareSession));
    }

    // Hashes the supplied options without resolving defaults.
    static String generateModelName(
            String modelPath, OnnxEvaluatorOptions options, boolean shareSession, Optional<OnnxPoolingOptions> pooling) {
        var fileName = Paths.get(modelPath).getFileName().toString();
        var extensionIndex = fileName.lastIndexOf('.');
        var baseName = extensionIndex < 0 ? fileName : fileName.substring(0, extensionIndex);
        var modelHash = ModelPathOrData.of(modelPath).calculateHash();
        var optionsHash = hashOptions(options);
        // Hash a fixed-width encoding of the model contents, options, session-sharing setting and pooling.
        // Without pooling, the encoding does not change, so existing model names stay the same.
        var identity = ByteBuffer.allocate(2 * Long.BYTES + Byte.BYTES + (pooling.isPresent() ? Long.BYTES : 0))
                .putLong(modelHash)
                .putLong(optionsHash)
                .put((byte) (shareSession ? 1 : 0));
        pooling.ifPresent(p -> identity.putLong(hash(poolingIdentity(p).getBytes(StandardCharsets.UTF_8))));
        var combinedHash = hash(identity.array());
        return baseName + "_" + Long.toHexString(combinedHash); // add hash to avoid conflicts
    }

    /**
     * Hashes the options that are part of model configuration in Triton.
     */
    private static long hashOptions(OnnxEvaluatorOptions options) {
        var encoding = "OnnxEvaluatorOptions[executionMode=" + options.executionMode() +
                       ", interOpThreads=" + options.interOpThreads() +
                       ", intraOpThreads=" + options.intraOpThreads() +
                       ", gpuDeviceNumber=" + options.gpuDeviceNumber() +
                       ", gpuDeviceRequired=" + options.gpuDeviceRequired() +
                       ", optimizeModel=" + options.optimizeModel() +
                       ", batchingMaxSize=" + options.batchingMaxSize() +
                       ", numModelInstances=" + options.numModelInstances() +
                       ", modelConfigOverride=" + options.modelConfigOverride() +
                       ", availableProcessors=" + options.availableProcessors() + "]";
        return hash(encoding.getBytes(StandardCharsets.UTF_8));
    }

    private static long hash(byte[] bytes) {
        return XXHashFactory.fastestInstance().hash64().hash(bytes, 0, bytes.length, 0);
    }

    // Returns the pooling parameters as the model config gets them. Embedders whose pooling differs only
    // in what the config leaves out, like the attention mask with CLS pooling, then share one model.
    static String poolingIdentity(OnnxPoolingOptions pooling) {
        var builder = ModelConfig.newBuilder();
        addPoolingParameters(builder, pooling);
        return new TreeMap<>(builder.getParametersMap()).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue().getStringValue())
                .collect(Collectors.joining(","));
    }

    private Path getModelDirInModelRepository(String modelName) {
        return modelRepositoryPath.resolve(modelName);
    }

    /**
     * Copies the model file, its external data files and config to a model repository directory that Triton has
     * access to. External data files are referenced by paths relative to the model file, so they are copied
     * to the same relative paths in the repository.
     */
    private void copyModelFilesToModelRepository(String modelName, String externalModelPath, String modelConfig) {
        var modelDirPath = getModelDirInModelRepository(modelName);
        var modelVersionPath = modelDirPath.resolve("1");
        var modelFilePath = modelVersionPath.resolve(MODEL_FILE_NAME);
        var modelConfigPath = modelDirPath.resolve("config.pbtxt");
        var sourceModelPath = Paths.get(externalModelPath);

        try {
            // Create directory for model name and version with correct permissions
            Files.createDirectories(
                    modelVersionPath,
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxrwxr-x")));

            Files.copy(sourceModelPath, modelFilePath, StandardCopyOption.REPLACE_EXISTING);

            var versionDirPath = modelVersionPath.normalize();
            for (var location : OnnxStreamParser.tryGetExternalDataLocations(sourceModelPath)) {
                var dataFilePath = versionDirPath.resolve(location).normalize();

                if (!dataFilePath.startsWith(versionDirPath) || dataFilePath.equals(versionDirPath)) {
                    throw new IllegalArgumentException(
                            "External data file '" + location + "' is outside the directory of " + externalModelPath);
                }
                // Reject an external data file named model.onnx because it would overwrite the main model file.
                if (dataFilePath.equals(versionDirPath.resolve(MODEL_FILE_NAME))) {
                    throw new IllegalArgumentException("External data file of " + externalModelPath +
                                                       " cannot be named " + MODEL_FILE_NAME);
                }

                Files.createDirectories(dataFilePath.getParent());
                Files.copy(sourceModelPath.resolveSibling(location), dataFilePath, StandardCopyOption.REPLACE_EXISTING);
                addReadPermissions(dataFilePath);
            }
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

    private String createModelConfig(String modelName, OnnxEvaluatorOptions options,
                                     Optional<OnnxPoolingOptions> pooling, boolean supportsBatching) {
        return options.modelConfigOverride()
                .map(path -> createModelConfigFromFile(path, modelName))
                .orElseGet(() -> createModelConfigFromOptions(modelName, options, pooling, supportsBatching))
                .toString();
    }

    // Assumes a batch dimension when the model cannot be read.
    // The model then gets the configured max batch size, and Triton reports any problem with the model.
    static boolean declaresBatchDimension(String modelPath) {
        var path = Path.of(modelPath);
        if (!Files.isRegularFile(path)) return true;
        try {
            return OnnxStreamParser.declaresBatchDimension(path);
        } catch (IOException e) {
            log.log(Level.WARNING, "Could not read the input and output shapes of model " + modelPath +
                                   ", assuming they have a batch dimension", e);
            return true;
        }
    }

    // Takes options with a resolved instance count, see resolveInstanceCount().
    private String createModelConfigFromOptions(String modelName, OnnxEvaluatorOptions options,
                                                Optional<OnnxPoolingOptions> pooling, boolean supportsBatching) {
        // Each model instance in Triton executes requests sequentially.
        // Concurrency comes from the number of instances.
        // This is different from EmbeddedOnnxRuntime, where one session handles all requests.
        // With a shared session, the thread counts match EmbeddedOnnxRuntime.createSessionOptions().
        // With separate sessions, the CPU cores are divided between the instances as intra-op threads.
        // Inter-op threads only matter in parallel execution mode.
        var executionModeValue = options.isParallel() ? "1" : "0";
        var numModelInstances = options.numModelInstances().orElseThrow(() ->
                new IllegalArgumentException("The number of model instances must be resolved before generating the model config"));
        var intraOpThreadCountValue = Integer.toString(shareSessionBetweenInstances
                ? options.intraOpThreads()
                : intraOpThreadsForSeparateSessions(options, numModelInstances));
        var interOpThreadCountValue = Integer.toString(options.effectiveInterOpThreads());
        // Triton's ONNX Runtime backend fails to load a model that does not support batching with a non-zero max batch size.
        // 0 means no batching.
        // For a model that supports batching, Triton's auto-complete would replace 0 with its own default and dynamic batching.
        var maxBatchSize = supportsBatching ? options.batchingMaxSize() : 0;

        var configBuilder = ModelConfig.newBuilder()
                .setName(modelName)
                .addInstanceGroup(createInstanceGroup(options, numModelInstances))
                .setPlatform("onnxruntime_onnx")
                .setMaxBatchSize(maxBatchSize)
                .putParameters(
                        "execution_mode",
                        ModelParameter.newBuilder()
                                .setStringValue(executionModeValue)
                                .build())
                .putParameters(
                        "enable_mem_arena",
                        ModelParameter.newBuilder()
                                .setStringValue("0")
                                .build())
                .putParameters(
                        "intra_op_thread_count",
                        ModelParameter.newBuilder()
                                .setStringValue(intraOpThreadCountValue)
                                .build())
                .putParameters(
                        "inter_op_thread_count",
                        ModelParameter.newBuilder()
                                .setStringValue(interOpThreadCountValue)
                                .build());

        addSessionSharingParameter(configBuilder);
        pooling.ifPresent(p -> addPoolingParameters(configBuilder, p));

        if (maxBatchSize > 1) {
            configBuilder.setDynamicBatching(ModelDynamicBatching.newBuilder().build());
        }

        // Triton's ONNX Runtime backend enables all optimizations when the graph optimization level is unspecified,
        // and maps level 2 to disabling all optimizations (ORT_DISABLE_ALL).
        if (!options.optimizeModel()) {
            configBuilder.setOptimization(ModelOptimizationPolicy.newBuilder()
                    .setGraph(ModelOptimizationPolicy.Graph.newBuilder()
                            .setLevel(2)
                            .build())
                    .build());
        }

        return configBuilder.build().toString();
    }

    // Instances with separate sessions each hold a copy of the model, so there is one instance by default.
    // Instances sharing a session share its thread pool. Each instance adds one calling thread.
    // Use one instance per CPU core, regardless of which device Triton selects.
    // availableProcessors is this container's core count, which is assumed to match the Triton sidecar's.
    static int defaultNumModelInstances(OnnxEvaluatorOptions options, boolean shareSession) {
        return shareSession ? Math.max(1, options.availableProcessors()) : 1;
    }

    // Instances with separate sessions each have their own thread pool, so the CPU cores are divided between them.
    // A negative thread factor is exactly that division, rounding up because it is better to
    // overutilize CPU than to underutilize.
    static int intraOpThreadsForSeparateSessions(OnnxEvaluatorOptions options, int numModelInstances) {
        return OnnxEvaluatorOptions.calculateThreads(-numModelInstances, options.availableProcessors());
    }

    static ModelInstanceGroup createInstanceGroup(OnnxEvaluatorOptions options, int numModelInstances) {
        var kind = options.gpuDeviceRequired() ? Kind.KIND_GPU
                : options.requestingGpu() ? Kind.KIND_AUTO : Kind.KIND_CPU;
        // The GPU list is left empty so Triton uses all GPUs, and can fall back to CPU for AUTO.
        return ModelInstanceGroup.newBuilder()
                .setCount(numModelInstances)
                .setKind(kind)
                .build();
    }

    // Sharing one session per device loads the model weights once per device instead of once per instance.
    // The parameter requires a patched Triton ONNX backend.
    private void addSessionSharingParameter(ModelConfig.Builder configBuilder) {
        if (!shareSessionBetweenInstances) {
            return;
        }

        configBuilder.putParameters(
                "share_session_between_instances",
                ModelParameter.newBuilder()
                        .setStringValue("true")
                        .build());
    }

    // Triton replaces the output with the pooled embeddings.
    // The caller normalizes, since it may first truncate the embeddings to the field size.
    // The parameters require an ONNX Runtime backend that supports pooling.
    static void addPoolingParameters(ModelConfig.Builder configBuilder, OnnxPoolingOptions pooling) {
        configBuilder
                .putParameters(
                        "pooling_strategy",
                        ModelParameter.newBuilder()
                                .setStringValue(pooling.strategy().configName())
                                .build())
                .putParameters(
                        "pooling_output",
                        ModelParameter.newBuilder()
                                .setStringValue(pooling.outputName())
                                .build());

        // Without a mask name, Triton's ONNX Runtime backend reads an "attention_mask" input if the model has one.
        // Otherwise it attends to every token, as Vespa's embedders do for a model without a mask input.
        // The backend fails to load a model that lacks a named mask input, even for CLS, which reads no mask.
        if (pooling.strategy() == OnnxPoolingOptions.Strategy.CLS || pooling.attentionMaskName().equals(DEFAULT_POOLING_ATTENTION_MASK)) return;
        configBuilder.putParameters(
                "pooling_attention_mask",
                ModelParameter.newBuilder().setStringValue(pooling.attentionMaskName()).build());
    }

    private String createModelConfigFromFile(Path configPath, String modelName) {
        String configStr;

        try {
            configStr = Files.readString(configPath);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read model config override file: " + configPath, e);
        }

        ModelConfig config;

        try {
            config = TextFormat.parse(configStr, ModelConfig.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse model config override:\n" + configStr, e);
        }

        // Replace model name with the one that includes model content and options hash to avoid conflicts.
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
