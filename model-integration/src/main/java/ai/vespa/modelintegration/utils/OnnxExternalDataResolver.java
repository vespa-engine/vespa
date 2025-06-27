// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.modelintegration.utils;

import ai.vespa.modelintegration.evaluator.OnnxStreamParser;
import com.yahoo.config.ModelReference;
import com.yahoo.config.UrlReference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Resolves external data files for an ONNX models.
 * Files are retrieved using {@link ModelPathHelper} and symlinked into a temporary directory together with the ONNX model file.
 *
 * @author bjorncs
 */
public class OnnxExternalDataResolver {

    private static final Logger log = Logger.getLogger(OnnxExternalDataResolver.class.getName());

    private final ModelPathHelper modelPathHelper;

    public OnnxExternalDataResolver(ModelPathHelper modelPathHelper) {
        this.modelPathHelper = modelPathHelper;
    }

    /** Create a resolver that does not support external models protected by bearer token */
    public OnnxExternalDataResolver() {
        this(new ModelPathHelperImpl(__ -> {
            throw new UnsupportedOperationException(
                    "Bearer token protected ONNX models are not supported in this context.");
        }));
    }

    /**
     * Resolves the ONNX model file and its external data files.
     * @return Path to the ONNX model file, which may be a symbolic link to the actual file.
     */
    public Path resolveOnnxModel(ModelReference ref) {
        var localPath = modelPathHelper.getModelPathResolvingIfNecessary(ref);
        if (shouldSkipExternalDataResolution(ref)) return localPath;
        try {
            var externalDataLocations = OnnxStreamParser.getExternalDataLocations(localPath);
            if (externalDataLocations.isEmpty()) return localPath;
            log.fine(() -> "Found external data locations for ONNX model '%s': %s".formatted(ref, externalDataLocations));
            var url = ref.url().get().value();
            var urlPrefix = url.substring(0, url.lastIndexOf('/') + 1);
            var externalDataFiles = new HashMap<Path, Path>();
            for (var location : externalDataLocations) {
                var dataFileUrl = urlPrefix + location;
                log.info("Downloading external data file '%s' for ONNX model '%s' using URL '%s'"
                        .formatted(location, localPath.getFileName(), dataFileUrl));
                var externalDataRef = ModelReference.unresolved(
                        Optional.empty(),
                        Optional.of(new UrlReference(dataFileUrl)),
                        ref.secretRef(), // keep secret reference if present
                        Optional.empty());
                var externalDataLocalPath = modelPathHelper.getModelPathResolvingIfNecessary(externalDataRef);
                externalDataFiles.put(location, externalDataLocalPath);
            }
            return createDirectoryWithExternalDataFiles(localPath, externalDataFiles)
                    .resolve(localPath.getFileName());
        } catch (IOException e) {
            log.warning(
                    "Failed to resolve external data files for ONNX model '%s': %s".formatted(ref, e.getMessage()));
            log.log(Level.FINE, e.toString(), e);

            // Fallback to returning local path
            return localPath;
        }
    }

    private static boolean shouldSkipExternalDataResolution(ModelReference ref) {
        if (ref.path() != null && ref.path().isPresent()) {
            log.fine(() -> "Model reference '%s' has no local path, cannot resolve external data files".formatted(ref));
            return true;
        }
        //noinspection OptionalAssignedToNull
        if (ref.url() == null || ref.url().isEmpty()) {
            log.fine(() -> "Model reference '%s' has no URL, cannot resolve external data files".formatted(ref));
            return true;
        }
        var modelUrl = ref.url().get().value();
        if (!modelUrl.endsWith(".onnx") || !modelUrl.contains("/")) {
            log.fine(() ->
                    "URL does refer to ONNX model file name: '%s'".formatted(modelUrl));
            return true;
        }
        return false;
    }

    /**
     * Creates a temporary directory where the ONNX model file and all external data files are symlinked in.
     *
     * @param model The ONNX model file.
     * @param externalDataFiles Mapping of relative location from model file to the path of the actual for external files.
     * @return path to the newly created directory containing symlinks to model and files.
     */
    static Path createDirectoryWithExternalDataFiles(Path model, Map<Path, Path> externalDataFiles) throws IOException {
        var tempDir = Files.createTempDirectory("onnx-model-");

        if (!Files.exists(model, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(model))
            throw new IllegalArgumentException("Model file does not exist: " + model);

        var targetModelPath = tempDir.resolve(model.getFileName());
        log.fine(() -> "Creating symlink for '%s' to '%s'".formatted(model, targetModelPath));
        Files.createSymbolicLink(targetModelPath, model.toAbsolutePath());

        // Create symlinks for all external data files
        for (var entry : externalDataFiles.entrySet()) {
            var relativeLocation = entry.getKey();
            var dataFile = entry.getValue().toAbsolutePath();

            if (!Files.exists(dataFile, LinkOption.NOFOLLOW_LINKS) || !Files.isRegularFile(dataFile))
                throw new IllegalArgumentException("External data file does not exist: " + dataFile);

            // Handle data files in subdirectories
            var targetPath = tempDir.resolve(relativeLocation);
            var parentDir = targetPath.getParent();
            if (parentDir != null && !Files.exists(parentDir, LinkOption.NOFOLLOW_LINKS)) {
                log.fine(() -> "Creating parent directory for symlink: '%s'".formatted(parentDir));
                Files.createDirectories(parentDir);
            }

            // Create the symlink to the external data file
            log.fine(() -> "Creating symlink for external data file '%s' to '%s'".formatted(dataFile, targetPath));
            Files.createSymbolicLink(targetPath, dataFile);
        }
        return tempDir;
    }
}
