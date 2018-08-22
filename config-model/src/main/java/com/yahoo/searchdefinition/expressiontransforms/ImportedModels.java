// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.expressiontransforms;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.yahoo.path.Path;
import com.yahoo.searchlib.rankingexpression.integration.ml.ImportedModel;
import com.yahoo.searchlib.rankingexpression.integration.ml.ModelImporter;
import com.yahoo.searchlib.rankingexpression.integration.ml.OnnxImporter;
import com.yahoo.searchlib.rankingexpression.integration.ml.TensorFlowImporter;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * All models imported from the models/ directory in the application package
 *
 * @author bratseth
 */
class ImportedModels {

    /** The cache of already imported models */
    private final Map<String, ImportedModel> importedModels = new HashMap<>();

    private final ImmutableList<ModelImporter> importers = ImmutableList.of(new TensorFlowImporter(), new OnnxImporter());

    ImportedModels() {
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
        ModelImporter importer = importers.stream().filter(item -> item.canImport(modelPath.toString())).findFirst().get();
        return importedModels.computeIfAbsent(modelName, __ -> importer.importModel(modelName, modelPath));
    }

    private static String toName(File modelPath) {
        Path localPath = Path.fromString(modelPath.toString()).getChildPath();
        return localPath.toString().replace("/", "_").replace('.', '_');
    }

}
