// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.configmodelview;

import com.yahoo.path.Path;
import com.yahoo.yolean.Exceptions;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * All models imported from the models/ directory in the application package.
 * If this is empty it may be due to either not having any models in the application package,
 * or this being created for a ZooKeeper application package, which does not have imported models.
 *
 * @author bratseth
 */
public class ImportedMlModels {

    /** All imported models, indexed by their names */
    private final Map<String, ImportedMlModel> importedModels;

    /** Models that were not imported due to some error */
    private final Map<String, String> skippedModels = new ConcurrentHashMap<>();

    /** Create a null imported models */
    public ImportedMlModels() {
        importedModels = Collections.emptyMap();
    }

    public ImportedMlModels(File modelsDirectory, ExecutorService executor, Collection<MlModelImporter> importers) {
        Map<String, Future<ImportedMlModel>> futureModels = new HashMap<>();

        // Find all subdirectories recursively which contains a model we can read
        importRecursively(modelsDirectory, executor, futureModels, importers, skippedModels);
        Map<String, ImportedMlModel> models = new HashMap<>();
        futureModels.forEach((name, future) -> {
            try {
                ImportedMlModel model = future.get();
                if (model != null) {
                    models.put(name, model);
                }
            } catch (InterruptedException | ExecutionException e) {
                skippedModels.put(name, Exceptions.toMessageString(e));
            }
        });
        importedModels = Collections.unmodifiableMap(models);
    }

    /**
     * Returns the model at the given location in the application package.
     *
     * @param modelPath the path to this model (file or directory, depending on model type)
     *                  under the application package, both from the root or relative to the
     *                  models directory works
     * @return the model at this path or null if none
     */
    public ImportedMlModel get(File modelPath) {
        return importedModels.get(toName(modelPath));
    }

    /** Returns an immutable collection of all the imported models */
    public Collection<ImportedMlModel> all() {
        return importedModels.values();
    }

    public Map<String, String> getSkippedModels() {
        return skippedModels;
    }

    private static void importRecursively(File dir,
                                          ExecutorService executor,
                                          Map<String, Future<ImportedMlModel>> models,
                                          Collection<MlModelImporter> importers,
                                          Map<String, String> skippedModels) {
        if ( ! dir.isDirectory()) return;

        Arrays.stream(dir.listFiles()).sorted().forEach(child -> {
            Optional<MlModelImporter> importer = findImporterOf(child, importers);
            if (importer.isPresent()) {
                String name = toName(child);
                Future<ImportedMlModel> existing = models.get(name);
                if (existing != null) {
                    try {
                        throw new IllegalArgumentException("The models in " + child + " and " + existing.get().source() +
                                                           " both resolve to the model name '" + name + "'");
                    } catch (InterruptedException | ExecutionException e) {}
                }

                Future<ImportedMlModel> future = executor.submit(() -> {
                    try {
                        return importer.get().importModel(name, child);
                    } catch (RuntimeException e) {
                        skippedModels.put(name, e.getMessage());
                    }
                    return null;
                });
                models.put(name, future);
            }
            else {
                importRecursively(child, executor, models, importers, skippedModels);
            }
        });
    }

    private static Optional<MlModelImporter> findImporterOf(File path, Collection<MlModelImporter> importers) {
        return importers.stream().filter(item -> item.canImport(path.toString())).findFirst();
    }

    private static String toName(File modelFile) {
        Path modelPath = Path.fromString(modelFile.toString());
        if (modelFile.isFile())
            modelPath = stripFileEnding(modelPath);
        String localPath = concatenateAfterModelsDirectory(modelPath);
        return localPath.replace('.', '_');
    }

    private static Path stripFileEnding(Path path) {
        int dotIndex = path.last().lastIndexOf(".");
        if (dotIndex <= 0) return path;
        return path.withLast(path.last().substring(0, dotIndex));
    }

    private static String concatenateAfterModelsDirectory(Path path) {
        boolean afterModels = false;
        StringBuilder result = new StringBuilder();
        for (String element : path.elements()) {
            if (afterModels) result.append(element).append("_");
            if (element.equals("models")) afterModels = true;
        }
        return result.substring(0, result.length()-1);
    }

}
