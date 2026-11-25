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
 * evaluators. Inference goes through Triton's OpenAI-compatible frontend, which relays it to the model's
 * llama-server, since Triton's KServe interface for llama.cpp has no tokenization.
 *
 * Both runtimes share Triton and its model repository, so each cleans up only its own leftovers,
 * telling the models apart by the backend in their configs, see {@link #isLlamacppModel}.
 * There should be only one instance, as for TritonOnnxRuntime, enforced by
 * RestartOnDeployForTritonRuntimeValidator.
 *
 * @author ingridhabrekke
 */
public class TritonLlamacppRuntime extends AbstractComponent implements LlamacppRuntime {

    private static final Logger log = Logger.getLogger(TritonLlamacppRuntime.class.getName());

    static final String BACKEND = "llamacpp";
    private static final String MODEL_FILE_NAME = "model.gguf";
    private static final String CONFIG_FILE_NAME = "config.pbtxt";
    // Each llamacpp.<flag> parameter becomes the llama-server flag --<flag>, an empty value a switch.
    private static final String FLAG_PREFIX = "llamacpp.";
    // The llama.cpp backend binds a Unix socket at a path that contains the model name, and Linux limits such a path
    // to 107 bytes. A model name keeps at most this many characters of the file name, so that it fits in that path.
    static final int MAX_BASE_NAME_LENGTH = 40;

    private final TritonModelRepositoryClient repositoryClient;
    private final TritonLlamacppClient llamacppClient;
    private final boolean isModelControlExplicit;
    private final Path modelRepositoryPath;

    // The number of evaluators using each model, by model name.
    // The map's per-key compute serializes loading and cleanup of the same model.
    private final ConcurrentMap<String, Integer> referenceCounts = new ConcurrentHashMap<>();

    @Inject
    public TritonLlamacppRuntime(TritonConfig config) {
        this(config, new TritonModelRepositoryClient(config), new TritonLlamacppClient(config));
    }

    // Takes ownership of the clients: deconstruct() closes them.
    TritonLlamacppRuntime(TritonConfig config, TritonModelRepositoryClient repositoryClient, TritonLlamacppClient llamacppClient) {
        this.repositoryClient = repositoryClient;
        this.llamacppClient = llamacppClient;
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
        deleteModelDir(modelRepositoryPath.resolve(modelName));
    }

    // Best-effort cleanup of the llama.cpp models left by a previous process. TritonOnnxRuntime cleans up the others.
    private void cleanUpLeftovers() {
        try {
            repositoryClient.unloadModels(modelName -> isLlamacppModel(modelRepositoryPath.resolve(modelName)));
        } catch (RuntimeException e) {
            log.log(Level.WARNING, () -> "Skipped unloading leftover llama.cpp models: " + Exceptions.toMessageString(e));
            return; // The models may still be loaded, so keep their files
        }
        if ( ! Files.exists(modelRepositoryPath)) return;
        try (var modelDirs = Files.list(modelRepositoryPath)) {
            modelDirs.filter(TritonLlamacppRuntime::isLlamacppModel).forEach(modelDir -> {
                log.warning(() -> "Deleting leftover model files from Triton model repository: " + modelDir);
                deleteModelDir(modelDir);
            });
        } catch (IOException e) {
            log.log(Level.WARNING, e, () -> "Failed to list files in Triton model repository: " + modelRepositoryPath);
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
        var baseName = extensionIndex < 0 ? fileName : fileName.substring(0, extensionIndex);
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
                       ", batchThreads=" + options.batchThreads() +
                       ", continuousBatching=" + options.continuousBatching() + "]";
        return hash(encoding.getBytes(StandardCharsets.UTF_8));
    }

    private static long hash(byte[] bytes) {
        return XXHashFactory.fastestInstance().hash64().hash(bytes, 0, bytes.length, 0);
    }

    /**
     * Returns the Triton model config of an embedding model with the given options.
     * The backend runs one llama-server per model with one slot per model instance, so concurrency comes from
     * the instance count. Its ctx-size is per slot, while the option is the total, as for llama.cpp itself.
     * Values the options leave unset are left to llama-server, except the GPU layers, where 0 means CPU only.
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
        putFlag(builder, "n-gpu-layers", Integer.toString(options.gpuLayers()));
        if (options.seed() > -1) putFlag(builder, "seed", Integer.toString(options.seed()));
        if (options.threads() > 0) putFlag(builder, "threads", Integer.toString(options.threads()));
        if (options.batchThreads() > 0) putFlag(builder, "threads-batch", Integer.toString(options.batchThreads()));
        // llama-server batches continuously by default.
        return builder.build().toString();
    }

    /**
     * One instance per llama-server slot. The kind only decides where Triton counts the instances,
     * e.g. the gpu_uuid label of its metrics, since llama-server places the model on the GPU itself.
     * A model that may use a GPU gets KIND_AUTO, which Triton resolves to KIND_GPU when there is a GPU, and
     * to KIND_CPU otherwise, as llama.cpp falls back to the CPU. It cannot name GPU 0: Triton refuses a GPU list
     * once it has resolved the kind to KIND_CPU. So a host with several GPUs gets the count per GPU, i.e. more slots.
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
        var modelDirPath = modelRepositoryPath.resolve(modelName);
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
        llamacppClient.close();
        repositoryClient.close();
    }

}
