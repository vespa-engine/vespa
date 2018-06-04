// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.ml;

import com.yahoo.searchlib.rankingexpression.integration.ml.importer.IntermediateGraph;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.tensorflow.GraphImporter;
import org.tensorflow.SavedModelBundle;

import java.io.IOException;

/**
 * Converts a saved TensorFlow model into a ranking expression and set of constants.
 *
 * @author bratseth
 * @author lesters
 */
public class TensorFlowImporter extends ModelImporter {

    /**
     * Imports a saved TensorFlow model from a directory.
     * The model should be saved as a .pbtxt or .pb file.
     * The name of the model is taken as the db/pbtxt file name (not including the file ending).
     *
     * @param modelName the name of the model to import, consisting of characters in [A-Za-z0-9_]
     * @param modelDir the directory containing the TensorFlow model files to import
     */
    public ImportedModel importModel(String modelName, String modelDir) {
        try (SavedModelBundle model = SavedModelBundle.load(modelDir, "serve")) {
            return importModel(modelName, model);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Could not import TensorFlow model from directory '" + modelDir + "'", e);
        }
    }

    /** Imports a TensorFlow model */
    ImportedModel importModel(String modelName, SavedModelBundle model) {
        try {
            IntermediateGraph graph = GraphImporter.importGraph(modelName, model);
            return convertIntermediateGraphToModel(graph);
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not import TensorFlow model '" + model + "'", e);
        }
    }


}
