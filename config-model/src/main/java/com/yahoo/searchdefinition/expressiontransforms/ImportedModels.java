package com.yahoo.searchdefinition.expressiontransforms;

import com.yahoo.searchlib.rankingexpression.integration.ml.ImportedModel;
import com.yahoo.searchlib.rankingexpression.integration.ml.ModelImporter;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Lazily loaded models imported from the models/ directory in the application package
 *
 * @author bratseth
 */
class ImportedModels {

    private final ModelImporter modelImporter;

    /** The cache of already imported models */
    private final Map<String, ImportedModel> importedModels = new HashMap<>();

    ImportedModels(ModelImporter modelImporter) {
        this.modelImporter = modelImporter;
    }

    /**
     * Returns the model at the given location in the application package (lazily loaded),
     *
     * @throws IllegalArgumentException if the model cannot be loaded
     */
    public ImportedModel imported(String modelName, File modelDir) {
        return importedModels.computeIfAbsent(modelName, __ -> modelImporter.importModel(modelName, modelDir));
    }

}
