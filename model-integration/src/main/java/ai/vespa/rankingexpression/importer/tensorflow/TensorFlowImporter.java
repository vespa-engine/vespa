// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.tensorflow;

import ai.vespa.rankingexpression.importer.ImportedModel;
import ai.vespa.rankingexpression.importer.ModelImporter;
import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModel;
import ai.vespa.rankingexpression.importer.onnx.OnnxImporter;
import com.yahoo.collections.Pair;
import com.yahoo.io.IOUtils;
import com.yahoo.system.ProcessExecuter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Converts a saved TensorFlow model into a ranking expression and set of constants.
 *
 * @author bratseth
 * @author lesters
 */
public class TensorFlowImporter extends ModelImporter {

    private static final Logger log = Logger.getLogger(TensorFlowImporter.class.getName());

    private final static int[] onnxOpsetsToTry = {12, 10, 8};

    private final OnnxImporter onnxImporter = new OnnxImporter();

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
        throw new IllegalArgumentException("Import of TensorFlow models is no longer supported");
    }

}
