// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.llm.clients.TritonConfig;
import ai.vespa.modelintegration.llamacpp.LlamacppEvaluator;
import ai.vespa.modelintegration.llamacpp.LlamacppOptions;
import ai.vespa.modelintegration.llamacpp.LlamacppRuntime;
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
import inference.ModelConfigOuterClass.ModelInstanceGroup;
import inference.ModelConfigOuterClass.ModelInstanceGroup.Kind;
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
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs GGUF embedding models with the llama.cpp backend of Triton Inference Server.
 *
 * Models are managed like in {@link TritonOnnxRuntime}: in EXPLICIT model control mode, each model is copied to the
 * model repository with a generated config, loaded through Triton's KServe gRPC API, and reference counted by its
 * evaluators. Inference goes through Triton's OpenAI-compatible frontend.
 *
 * There should be only one instance at a time, since in EXPLICIT model control mode, a new instance unloads
 * the llama.cpp models in the repository, also those that another instance still uses.
 *
 * @author ingridhabrekke
 * @author glebashnik
 */
public class TritonLlamacppRuntime extends AbstractComponent implements LlamacppRuntime {

    private static final Logger log = Logger.getLogger(TritonLlamacppRuntime.class.getName());

    static final String BACKEND = "llamacpp";
    private static final String MODEL_FILE_NAME = "model.gguf";
    private static final String CONFIG_FILE_NAME = "config.pbtxt";
    // Each llamacpp.<flag> parameter sets the llama.cpp option --<flag>, an empty value a switch.
    private static final String FLAG_PREFIX = "llamacpp.";
    // Triton's llama.cpp backend fails to load a model whose name is longer than about 80 bytes.
    // A model name keeps at most this many characters of the file name, each one byte, since a non-ASCII
    // character takes several bytes in UTF-8.
    static final int MAX_BASE_NAME_LENGTH = 40;

    private final TritonLlamacppClient llamacppClient;
    private final TritonModelRepositoryClient repositoryClient;
    private final boolean isModelControlExplicit;
    private final Path modelRepositoryPath;

    // The number of evaluators using each model, by model name.
    // The map's per-key compute serializes loading and cleanup of the same model.
    private final ConcurrentMap<String, Integer> referenceCounts = new ConcurrentHashMap<>();

    @Inject
    public TritonLlamacppRuntime(TritonConfig config) {
        this(config, new TritonLlamacppClient(config), new TritonModelRepositoryClient(config));
    }

    // Takes ownership of the clients: deconstruct() closes them.
    TritonLlamacppRuntime(TritonConfig config, TritonLlamacppClient llamacppClient, TritonModelRepositoryClient repositoryClient) {
        this.llamacppClient = llamacppClient;
        this.repositoryClient = repositoryClient;
        this.isModelControlExplicit = config.modelControlMode() == TritonConfig.ModelControlMode.EXPLICIT;
        this.modelRepositoryPath = Path.of(Defaults.getDefaults().underVespaHome(config.modelRepositoryPath()));
        log.info("Creating Triton llama.cpp runtime with endpoint " + config.llamacppEndpoint());

        if (isModelControlExplicit) {
            cleanUpLeftovers();
        }
    }

    @Override
    public LlamacppEvaluator evaluatorOf(String modelPath, LlamacppOptions options) {
        var modelName = generateModelName(modelPath, options);
        var modelReference = referenceModel(modelName, modelPath, options);
        return new TritonLlamacppEvaluator(modelName, modelReference, llamacppClient, repositoryClient, isModelControlExplicit);
    }

    // Returns a reference to the model, first copying and loading it into Triton if this is its first reference.
    // A model that already has references is in use: copying over it, or cleaning it up when a load fails,
    // would break the evaluators using it. If Triton has lost it, those evaluators reload it, see TritonLlamacppEvaluator.
    private ResourceReference referenceModel(String modelName, String modelPath, LlamacppOptions options) {
        referenceCounts.compute(modelName, (key, count) -> {
            if (count != null) return count + 1;
            ensureModelReady(modelName, modelPath, options);
            return 1;
        });

        var closed = new AtomicBoolean(false);
        return () -> {
            if (closed.getAndSet(true))
                throw new IllegalStateException("The reference to model " + modelName + " is already closed");
            releaseModel(modelName);
        };
    }

    private void releaseModel(String modelName) {
        referenceCounts.compute(modelName, (key, count) -> {
            if (count == null) return null; // Already removed, e.g. force-released by deconstruct()
            if (count > 1) return count - 1;
            if (isModelControlExplicit) cleanUpModel(modelName);
            return null;
        });
    }

    // Uses an already-ready model, or copies the model into the repository and loads it into Triton.
    private void ensureModelReady(String modelName, String modelPath, LlamacppOptions options) {
        if ( ! isModelControlExplicit) return;

        try {
            if (repositoryClient.isModelReady(modelName)) return; // Adopt a model that Triton already has ready
        } catch (TritonException | TimeoutException e) {
            // Still attempt to copy and load the model below.
        }

        try {
            copyModelFilesToModelRepository(modelName, modelPath, createModelConfig(modelName, options));
            repositoryClient.loadUntilModelReady(modelName);
        } catch (RuntimeException e) {
            cleanUpModel(modelName); // Best effort
            throw e;
        }
    }

    // Best-effort unloading of a model from Triton and removing its files from the model repository.
    private void cleanUpModel(String modelName) {
        try {
            repositoryClient.unloadUntilModelNotReady(modelName);
        } catch (RuntimeException e) {
            // Keep the files if unload fails: deleting them while the model may still be loaded would break it.
            log.log(Level.WARNING, e, () -> "Failed to unload model " + modelName + ", keeping its files");
            return;
        }
        deleteModelFilesFromModelRepository(modelName);
    }

    // Best-effort cleanup of the llama.cpp models left by a previous process. TritonOnnxRuntime cleans up the others.
    private void cleanUpLeftovers() {
        try {
            repositoryClient.unloadModels(modelName -> isLlamacppModel(getModelDirInModelRepository(modelName)));
            deleteAllModelFilesFromModelRepository();
        } catch (RuntimeException e) {
            log.log(Level.WARNING, () -> "Skipped cleaning up leftover llama.cpp models: " + Exceptions.toMessageString(e));
        }
    }

    private void deleteAllModelFilesFromModelRepository() {
        if ( ! Files.exists(modelRepositoryPath)) return;
        try (var modelDirs = Files.list(modelRepositoryPath)) {
            modelDirs.filter(TritonLlamacppRuntime::isLlamacppModel).forEach(modelDir -> {
                log.warning(() -> "Deleting leftover model files from Triton model repository: " + modelDir);
                deleteModelDir(modelDir);
            });
        } catch (IOException e) {
            log.log(Level.SEVERE, e, () -> "Failed to list files in Triton model repository: " + modelRepositoryPath);
        }
    }

    /**
     * Returns whether the model directory holds a llama.cpp model, i.e. a model of this runtime.
     * The config is written before the model file, so a model is recognized while it is being copied.
     */
    static boolean isLlamacppModel(Path modelDir) {
        var configPath = modelDir.resolve(CONFIG_FILE_NAME);
        if ( ! Files.isRegularFile(configPath)) return false;
        try {
            var config = ModelConfig.newBuilder();
            TextFormat.getParser().merge(Files.readString(configPath), config);
            return BACKEND.equals(config.getBackend());
        } catch (IOException | RuntimeException e) {
            return false; // Not a config of this runtime
        }
    }

    /** Hashes the model contents and options, so that different models or options get different names. */
    static String generateModelName(String modelPath, LlamacppOptions options) {
        var fileName = Paths.get(modelPath).getFileName().toString();
        var extensionIndex = fileName.lastIndexOf('.');
        var baseName = (extensionIndex < 0 ? fileName : fileName.substring(0, extensionIndex))
                .replaceAll("[^A-Za-z0-9._-]", "_");
        if (baseName.length() > MAX_BASE_NAME_LENGTH) baseName = baseName.substring(0, MAX_BASE_NAME_LENGTH);
        var identity = ByteBuffer.allocate(2 * Long.BYTES)
                .putLong(ModelPathOrData.of(modelPath).calculateHash())
                .putLong(hashOptions(options));
        return baseName + "_" + Long.toHexString(hash(identity.array()));
    }

    // An explicit encoding, so that names don't change with the record's toString().
    private static long hashOptions(LlamacppOptions options) {
        var encoding = "LlamacppOptions[parallel=" + options.parallel() +
                       ", contextSize=" + options.contextSize() +
                       ", logicalMaxBatchSize=" + options.logicalMaxBatchSize() +
                       ", physicalMaxBatchSize=" + options.physicalMaxBatchSize() +
                       ", gpuLayers=" + options.gpuLayers() +
                       ", pooling=" + options.pooling().map(Enum::name).orElse("") +
                       ", seed=" + options.seed() +
                       ", threads=" + options.threads() +
                       ", batchThreads=" + options.batchThreads() + "]";
        return hash(encoding.getBytes(StandardCharsets.UTF_8));
    }

    private static long hash(byte[] bytes) {
        return XXHashFactory.fastestInstance().hash64().hash(bytes, 0, bytes.length, 0);
    }

    /**
     * Returns the Triton model config of an embedding model with the given options.
     * Each model instance serves one request at a time, so the instance count is parallel.
     * The backend's ctx-size is the context of one instance, while contextSize is the total for all instances,
     * as in the embedded runtime, so it is divided by parallel.
     * Values the options leave unset are left to llama.cpp. For the GPU layers, that means using a GPU
     * if there is one, while 0 means no layers on a GPU.
     */
    static String createModelConfig(String modelName, LlamacppOptions options) {
        var builder = ModelConfig.newBuilder()
                .setName(modelName)
                .setBackend(BACKEND)
                .addInstanceGroup(instanceGroup(options));
        putFlag(builder, "embeddings", "");
        options.pooling().ifPresent(pooling -> putFlag(builder, "pooling", pooling.name().toLowerCase(Locale.ROOT)));
        if (options.contextSize() > 0)
            putFlag(builder, "ctx-size", Integer.toString(Math.max(1, options.contextSize() / options.parallel())));
        if (options.logicalMaxBatchSize() > 0) putFlag(builder, "batch-size", Integer.toString(options.logicalMaxBatchSize()));
        if (options.physicalMaxBatchSize() > 0) putFlag(builder, "ubatch-size", Integer.toString(options.physicalMaxBatchSize()));
        if (options.gpuLayers() >= 0) putFlag(builder, "n-gpu-layers", Integer.toString(options.gpuLayers()));
        if (options.seed() > -1) putFlag(builder, "seed", Integer.toString(options.seed()));
        if (options.threads() > 0) putFlag(builder, "threads", Integer.toString(options.threads()));
        if (options.batchThreads() > 0) putFlag(builder, "threads-batch", Integer.toString(options.batchThreads()));
        return builder.build().toString();
    }

    /**
     * The kind does not decide whether llama.cpp uses a GPU; the GPU layers do.
     * A model that may use a GPU gets KIND_AUTO, which Triton resolves to KIND_GPU on a host with a GPU, and to
     * KIND_CPU otherwise. The group cannot be pinned to GPU 0, since Triton rejects a GPU list when the kind
     * resolves to KIND_CPU. As a consequence, on a host with several GPUs, Triton creates the instance count
     * per GPU.
     */
    private static ModelInstanceGroup instanceGroup(LlamacppOptions options) {
        var group = ModelInstanceGroup.newBuilder().setCount(options.parallel());
        return group.setKind(options.gpuLayers() == 0 ? Kind.KIND_CPU : Kind.KIND_AUTO).build();
    }

    private static void putFlag(ModelConfig.Builder builder, String flag, String value) {
        builder.putParameters(FLAG_PREFIX + flag, ModelParameter.newBuilder().setStringValue(value).build());
    }

    /**
     * Writes the config and copies the model file to a model repository directory that Triton has access to.
     * The config is written first, see {@link #isLlamacppModel}.
     */
    private void copyModelFilesToModelRepository(String modelName, String modelPath, String modelConfig) {
        var modelDirPath = getModelDirInModelRepository(modelName);
        var modelVersionPath = modelDirPath.resolve("1");
        var modelFilePath = modelVersionPath.resolve(MODEL_FILE_NAME);
        var modelConfigPath = modelDirPath.resolve(CONFIG_FILE_NAME);

        try {
            Files.createDirectories(modelVersionPath,
                                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxrwxr-x")));
            Files.writeString(modelConfigPath, modelConfig);
            addReadPermissions(modelConfigPath);
            Files.copy(Paths.get(modelPath), modelFilePath, StandardCopyOption.REPLACE_EXISTING);
            addReadPermissions(modelFilePath); // Triton must be able to read the files
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to copy model file to repository", e);
        }
    }

    private Path getModelDirInModelRepository(String modelName) {
        return modelRepositoryPath.resolve(modelName);
    }

    private void deleteModelFilesFromModelRepository(String modelName) {
        deleteModelDir(getModelDirInModelRepository(modelName));
    }

    private static void deleteModelDir(Path modelDir) {
        if (Files.exists(modelDir) && ! IOUtils.recursiveDeleteDir(modelDir.toFile()))
            log.warning(() -> "Failed to delete model files from Triton model repository: " + modelDir);
    }

    private static void addReadPermissions(Path path) throws IOException {
        var permissions = Files.getPosixFilePermissions(path);
        permissions.add(PosixFilePermission.GROUP_READ);
        permissions.add(PosixFilePermission.OTHERS_READ);
        Files.setPosixFilePermissions(path, permissions);
    }

    @Override
    public void deconstruct() {
        for (var modelName : referenceCounts.keySet()) {
            referenceCounts.compute(modelName, (key, count) -> {
                if (count != null && isModelControlExplicit) cleanUpModel(modelName);
                return null;
            });
        }
        try {
            llamacppClient.close();
        } finally {
            repositoryClient.close();
        }
    }

}
