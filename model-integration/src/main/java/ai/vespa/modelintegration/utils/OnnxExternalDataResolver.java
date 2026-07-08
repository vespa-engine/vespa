// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.modelintegration.utils;

import ai.vespa.modelintegration.evaluator.OnnxStreamParser;
import com.yahoo.config.ModelReference;
import com.yahoo.config.UrlReference;
import com.yahoo.text.Text;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Resolves external data files for ONNX models.
 * Files are retrieved using {@link ModelPathHelper} and hard-linked (or copied, when hard-linking is not possible)
 * into a temporary directory together with the ONNX model file.
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
     * @return Path to the ONNX model file. When the model has external data files, this is a path inside a temporary
     *         directory where the model and its data files are hard-linked (or copied); otherwise it is the resolved
     *         local path of the model itself.
     */
    public Path resolveOnnxModel(ModelReference ref) {
        var localPath = modelPathHelper.getModelPathResolvingIfNecessary(ref);
        if (shouldSkipExternalDataResolution(ref)) return localPath;
        try {
            var externalDataLocations = OnnxStreamParser.getExternalDataLocations(localPath);
            if (externalDataLocations.isEmpty()) return localPath;
            log.fine(() -> Text.format("Found external data locations for ONNX model '%s': %s", ref, externalDataLocations));
            var url = ref.url().get().value();
            var urlPrefix = url.substring(0, url.lastIndexOf('/') + 1);
            var externalDataFiles = new HashMap<Path, Path>();
            for (var location : externalDataLocations) {
                var dataFileUrl = urlPrefix + location;
                log.info(Text.format("Downloading external data file '%s' for ONNX model '%s' using URL '%s'", location, localPath.getFileName(), dataFileUrl));
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
                    Text.format("Failed to resolve external data files for ONNX model '%s': %s", ref, e.getMessage()));
            log.log(Level.FINE, e.toString(), e);

            // Fallback to returning local path
            return localPath;
        }
    }

    private static boolean shouldSkipExternalDataResolution(ModelReference ref) {
        if (ref.path() != null && ref.path().isPresent()) {
            log.fine(() -> Text.format("Model reference '%s' has no local path, cannot resolve external data files", ref));
            return true;
        }
        //noinspection OptionalAssignedToNull
        if (ref.url() == null || ref.url().isEmpty()) {
            log.fine(() -> Text.format("Model reference '%s' has no URL, cannot resolve external data files", ref));
            return true;
        }
        var modelUrl = ref.url().get().value();
        if (!modelUrl.endsWith(".onnx") || !modelUrl.contains("/")) {
            log.fine(() ->
                    Text.format("URL does refer to ONNX model file name: '%s'", modelUrl));
            return true;
        }
        return false;
    }

    /**
     * Creates a temporary directory where the ONNX model file and all external data files are hard-linked in.
     *
     * @param model The ONNX model file.
     * @param externalDataFiles Mapping of relative location from model file to the path of the actual for external files.
     * @return path to the newly created directory containing hard links to model and files.
     */
    static Path createDirectoryWithExternalDataFiles(Path model, Map<Path, Path> externalDataFiles) throws IOException {
        if (!Files.isRegularFile(model))
            throw new IllegalArgumentException("Model file does not exist or is not a regular file: " + model);

        var tempDir = Files.createTempDirectory("onnx-model-");
        try {
            var targetModelPath = tempDir.resolve(model.getFileName());
            log.fine(() -> Text.format("Linking '%s' into '%s'", model, targetModelPath));
            linkOrCopy(model, targetModelPath);

            // Link all external data files next to the model
            for (var entry : externalDataFiles.entrySet()) {
                var relativeLocation = entry.getKey();
                var dataFile = entry.getValue().toAbsolutePath();

                if (!Files.exists(dataFile, LinkOption.NOFOLLOW_LINKS) || !Files.isRegularFile(dataFile))
                    throw new IllegalArgumentException("External data file does not exist: " + dataFile);

                // Handle data files in subdirectories
                var targetPath = tempDir.resolve(relativeLocation);
                var parentDir = targetPath.getParent();
                if (parentDir != null && !Files.exists(parentDir, LinkOption.NOFOLLOW_LINKS)) {
                    log.fine(() -> Text.format("Creating parent directory for external data file: '%s'", parentDir));
                    Files.createDirectories(parentDir);
                }

                log.fine(() -> Text.format("Linking external data file '%s' into '%s'", dataFile, targetPath));
                linkOrCopy(dataFile, targetPath);
            }
            return tempDir;
        } catch (RuntimeException | IOException e) {
            // Avoid leaking a partially populated temp directory (which may contain large copied files) on failure.
            deleteRecursivelyQuietly(tempDir);
            throw e;
        }
    }

    private static void deleteRecursivelyQuietly(Path dir) {
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.log(Level.FINE, e, () -> Text.format("Failed to delete '%s' during cleanup", p));
                }
            });
        } catch (IOException e) {
            log.log(Level.FINE, e, () -> Text.format("Failed to clean up temporary directory '%s'", dir));
        }
    }

    /**
     * Hard-links {@code source} to {@code target}, falling back to a plain copy if hard linking is not possible
     * (e.g. source and target reside on different filesystems).
     *
     * <p>Hard links are used rather than symbolic links because onnxruntime (>= 1.24) validates that external data
     * paths resolve (via {@code weakly_canonical}, which follows symlinks) to a location contained within the model
     * directory. Symlinking the downloaded files — which live in separate per-URL download directories — makes the
     * resolved paths escape the model directory and fail validation. Hard links have no symlink to follow, so they
     * resolve to their own path inside the temporary model directory.
     */
    private static void linkOrCopy(Path source, Path target) throws IOException {
        // Resolve to the real file so we link/copy the underlying inode, not a symlink in the download directory.
        var src = source.toRealPath();
        try {
            Files.createLink(target, src);
        } catch (UnsupportedOperationException | FileSystemException e) {
            log.fine(() -> Text.format(
                    "Hard link from '%s' to '%s' failed (%s); copying instead", src, target, e.getMessage()));
            Files.copy(src, target);
        }
    }
}
