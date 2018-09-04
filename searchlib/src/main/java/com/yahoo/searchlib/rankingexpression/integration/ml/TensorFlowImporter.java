// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.ml;

import com.yahoo.searchlib.rankingexpression.integration.ml.importer.IntermediateGraph;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.tensorflow.GraphImporter;
import org.tensorflow.SavedModelBundle;

import java.io.File;
import java.io.IOException;

/**
 * Converts a saved TensorFlow model into a ranking expression and set of constants.
 *
 * @author bratseth
 * @author lesters
 */
public class TensorFlowImporter extends ModelImporter {

    @Override
    public boolean canImport(String modelPath) {
        File modelDir = new File(modelPath);
        if ( ! modelDir.isDirectory()) return false;

        // No other model types are stored in protobuf files thus far
        for (File file : modelDir.listFiles()) {
            if (file.toString().endsWith(".pbtxt")) return true;
            if (file.toString().endsWith(".pb")) return true;
        }
        return false;
    }

    /**
     * Imports a saved TensorFlow model from a directory.
     * The model should be saved as a .pbtxt or .pb file.
     *
     * @param modelName the name of the model to import, consisting of characters in [A-Za-z0-9_]
     * @param modelDir the directory containing the TensorFlow model files to import
     */
    @Override
    public ImportedModel importModel(String modelName, String modelDir) {
        try (SavedModelBundle model = SavedModelBundle.load(modelDir, "serve")) {
            return importModel(modelName, modelDir, model);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Could not import TensorFlow model from directory '" + modelDir + "'", e);
        }
    }

    /** Imports a TensorFlow model */
    ImportedModel importModel(String modelName, String modelDir, SavedModelBundle model) {
        try {
            IntermediateGraph graph = GraphImporter.importGraph(modelName, model);
            return convertIntermediateGraphToModel(graph, modelDir);
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not import TensorFlow model '" + model + "'", e);
        }
    }


}
