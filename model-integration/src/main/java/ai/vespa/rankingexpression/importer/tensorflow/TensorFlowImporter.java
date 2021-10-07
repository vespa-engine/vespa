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
        return convertToOnnxAndImport(modelName, modelDir);
    }

    private ImportedModel convertToOnnxAndImport(String modelName, String modelDir) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("tf2onnx");
            String convertedPath = tempDir.toString() + File.separatorChar + "converted.onnx";
            String outputOfLastConversionAttempt = "";
            for (int opset : onnxOpsetsToTry) {
                log.info("Converting TensorFlow model '" + modelDir + "' to ONNX with opset " + opset + "...");
                Pair<Integer, String> res = convertToOnnx(modelDir, convertedPath, opset);
                if (res.getFirst() == 0) {
                    log.info("Conversion to ONNX with opset " + opset + " successful.");

                    /*
                     * For now we have to import tensorflow models as native Vespa expressions.
                     * The temporary ONNX file that is created by conversion needs to be put
                     * in the application package so it can be file distributed.
                     */
                    return onnxImporter.importModelAsNative(modelName, convertedPath, ImportedMlModel.ModelType.TENSORFLOW);
                }
                log.fine("Conversion to ONNX with opset " + opset + " failed. Reason: " + res.getSecond());
                outputOfLastConversionAttempt = res.getSecond();
            }
            throw new IllegalArgumentException("Unable to convert TensorFlow model in '" + modelDir + "' to ONNX. " +
                    "Reason: " + outputOfLastConversionAttempt);
        } catch (IOException e) {
            throw new IllegalArgumentException("Conversion from TensorFlow to ONNX failed for '" + modelDir + "'");
        } finally {
            if (tempDir != null) {
                IOUtils.recursiveDeleteDir(tempDir.toFile());
            }
        }
    }

    private Pair<Integer, String> convertToOnnx(String savedModel, String output, int opset) throws IOException {
        ProcessExecuter executer = new ProcessExecuter();
        String job = "vespa-convert-tf2onnx --saved-model " + savedModel + " --output " + output + " --opset " + opset
                + " --use-graph-names";  // for backward compatibility with tf2onnx versions < 1.9.1
        return executer.exec(job);
    }

}
