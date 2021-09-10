// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer;

import com.yahoo.path.Path;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// TODO: Remove this class after November 2018
public class ImportedModels {

    /** All imported models, indexed by their names */
    private final Map<String, ImportedModel> importedModels;

    /** Create a null imported models */
    public ImportedModels() {
        importedModels = Collections.emptyMap();
    }

    public ImportedModels(File modelsDirectory, Collection<ModelImporter> importers) {
        Map<String, ImportedModel> models = new HashMap<>();

        // Find all subdirectories recursively which contains a model we can read
        importRecursively(modelsDirectory, models, importers);
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
    public ImportedModel get(File modelPath) {
        return importedModels.get(toName(modelPath));
    }

    /** Returns an immutable collection of all the imported models */
    public Collection<ImportedModel> all() {
        return importedModels.values();
    }

    private static void importRecursively(File dir,
                                          Map<String, ImportedModel> models,
                                          Collection<ModelImporter> importers) {
        if ( ! dir.isDirectory()) return;

        Arrays.stream(dir.listFiles()).sorted().forEach(child -> {
            Optional<ModelImporter> importer = findImporterOf(child, importers);
            if (importer.isPresent()) {
                String name = toName(child);
                ImportedModel existing = models.get(name);
                if (existing != null)
                    throw new IllegalArgumentException("The models in " + child + " and " + existing.source() +
                                                       " both resolve to the model name '" + name + "'");
                models.put(name, importer.get().importModel(name, child));
            }
            else {
                importRecursively(child, models, importers);
            }
        });
    }

    private static Optional<ModelImporter> findImporterOf(File path, Collection<ModelImporter> importers) {
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
