package com.yahoo.searchlib.rankingexpression.integration.ml;

import com.yahoo.searchlib.rankingexpression.integration.ml.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.tensorflow.TensorConverter;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.tensorflow.GraphImporter;
import com.yahoo.tensor.serialization.JsonFormat;
import com.yahoo.yolean.Exceptions;
import org.tensorflow.SavedModelBundle;

import java.nio.charset.StandardCharsets;

/**
 * Converts TensorFlow Variables to the Vespa document format.
 * Intended to be used from the command line to convert trained tensors to document form.
 *
 * @author bratseth
 */
public class VariableConverter {

    /**
     * Reads the tensor with the given TensorFlow name at the given model location,
     * and encodes it as UTF-8 Vespa document tensor JSON having the given ordered tensor type.
     * Note that order of dimensions in the tensor type does matter as the TensorFlow tensor
     * tensor dimensions are implicitly ordered.
     */
    public static byte[] importVariable(String modelDir, String tensorFlowVariableName, String orderedTypeSpec) {
        try (SavedModelBundle bundle = SavedModelBundle.load(modelDir, "serve")) {
            return JsonFormat.encode(TensorConverter.toVespaTensor(GraphImporter.readVariable(tensorFlowVariableName,
                                                                                                   bundle),
                                                                   OrderedTensorType.fromSpec(orderedTypeSpec)));
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Could not import TensorFlow model from directory '" + modelDir + "'", e);
        }
    }

    public static void main(String[] args) {
        if ( args.length != 3) {
            System.out.println("Converts a TensorFlow variable into Vespa tensor document field value JSON:");
            System.out.println("A JSON map containing a 'cells' array, see");
            System.out.println("http://docs.vespa.ai/documentation/reference/document-json-put-format.html#tensor)");
            System.out.println("");
            System.out.println("Arguments: modelDirectory tensorFlowVariableName orderedTypeSpec");
            System.out.println(" - modelDirectory: The directory of the TensorFlow SavedModel");
            System.out.println(" - tensorFlowVariableName: The name of the TensorFlow variable to convert");
            System.out.println(" - orderedTypeSpec: The tensor type, e.g tensor(b[],a[10]), where dimensions are ");
            System.out.println("                    ordered as given in the deployment log message starting by ");
            System.out.println("                    'Importing TensorFlow variable'");
            return;
        }

        try {
            System.out.println(new String(importVariable(args[0], args[1], args[2]), StandardCharsets.UTF_8));
        }
        catch (Exception e) {
            System.err.println("Import failed: " + Exceptions.toMessageString(e));
        }
    }

}
