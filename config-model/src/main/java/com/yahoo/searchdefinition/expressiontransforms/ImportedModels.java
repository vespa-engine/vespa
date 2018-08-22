// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.expressiontransforms;

import com.yahoo.path.Path;
import com.yahoo.searchlib.rankingexpression.integration.ml.ImportedModel;
import com.yahoo.searchlib.rankingexpression.integration.ml.ModelImporter;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * All models imported from the models/ directory in the application package
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
     * @param modelPath the full path to this model (file or directory, depending on model type)
     *                  under the application package
     * @throws IllegalArgumentException if the model cannot be loaded
     */
    public ImportedModel get(File modelPath) {
        String modelName = toName(modelPath);
        return importedModels.computeIfAbsent(modelName, __ -> modelImporter.importModel(modelName, modelPath));
    }

    private static String toName(File modelPath) {
        Path localPath = Path.fromString(modelPath.toString()).getChildPath();
        return localPath.toString().replace("/", "_").replace('.', '_');
    }

}
