// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.onnx;

import ai.vespa.rankingexpression.importer.ImportedModel;
import ai.vespa.rankingexpression.importer.tensorflow.TensorFlowImporter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.collections.Pair;
import com.yahoo.system.ProcessExecuter;
import com.yahoo.tensor.Tensor;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.tensorflow.SavedModelBundle;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Tries to convert a set of TensorFlow models to ONNX using the tf2onnx tool,
 * and asserts that the result when evaluated on TensorFlow, the imported
 * TensorFlow model and the imported ONNX model are equal.
 *
 * Requires the tf2onnx tool to be installed so the test itself should be ignored.
 *
 * @author lesters
 */
public class Tf2OnnxImportTestCase extends TestableModel {

    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    @Test
    @Ignore
    public void testOnnxConversionAndImport() {
        Report report = new Report();
        for (int i = 11; i < 12; ++i) {
            testModelsWithOpset(report, i);
        }
        System.out.println(report);
    }

    private void testModelsWithOpset(Report report, int opset) {
        String [] models = {
            "tensorflow/mnist/saved/",
            "tensorflow/mnist_softmax/saved/",
            "tensorflow/9662/",
            "tensorflow/regression/test1/",
            "tensorflow/regression/test2/",
            "tensorflow/softmax/saved/",
            "tensorflow/blog/saved/",
            "tensorflow/batch_norm/saved/",
            "tensorflow/dropout/saved/",
            "tensorflow/external/Model_A/optimized_v2/",
            "tensorflow/external/Model_B/factorization_machine_v1/export/optimized/",
            "tensorflow/external/Model_B/factorization_machine_v1/export/standard/",
            "tensorflow/external/Model_C/factorization_machine_v1/export/optimized/",
            "tensorflow/external/Model_C/factorization_machine_v1/export/standard/",
            "tensorflow/external/modelv1/",
            "tensorflow/external/modelv2/"
        };
        for (String model : models) {
            try {
                testModelWithOpset(report, opset, "src/test/models/" + model);
            } catch (Exception e) {
                report.add(model, opset, false, "Exception: " + e.getMessage());
            }
        }
    }

    private boolean testModelWithOpset(Report report, int opset, String tfModel) throws IOException {
        String onnxModel = Paths.get(testFolder.getRoot().getAbsolutePath(), "converted.onnx").toString();

        var res = tf2onnxConvert(tfModel, onnxModel, opset);
        if (res.getFirst() != 0) {
            return reportAndFail(report, opset, tfModel, "tf2onnx conversion failed: " + res.getSecond());
        }

        SavedModelBundle tensorFlowModel = SavedModelBundle.load(tfModel, "serve");
        ImportedModel model = new TensorFlowImporter().importModel("test", tfModel, tensorFlowModel);
        ImportedModel onnxImportedModel = new OnnxImporter().importModel("test", onnxModel);

        if (model.signature("serving_default").skippedOutputs().size() > 0) {
            return reportAndFail(report, opset, tfModel, "Failed to import model from TensorFlow due to skipped outputs");
        }
        if (onnxImportedModel.signature("default").skippedOutputs().size() > 0) {
            return reportAndFail(report, opset, tfModel, "Failed to import model from ONNX due to skipped outputs");
        }

        ImportedModel.Signature sig = model.signatures().values().iterator().next();
        String output = sig.outputs().values().iterator().next();
        String onnxOutput = onnxImportedModel.signatures().values().iterator().next().outputs().values().iterator().next();

        Tensor tfResult = evaluateTF(tensorFlowModel, output, model.inputs());
        Tensor vespaResult = evaluateVespa(model, output, model.inputs());
        Tensor onnxResult = evaluateVespa(onnxImportedModel, onnxOutput, model.inputs());

        if ( ! tfResult.equals(vespaResult) ) {
            return reportAndFail(report, opset, tfModel, "Diff between tf and imported tf evaluation:\n\t" + tfResult + "\n\t" + vespaResult);
        }
        if ( ! vespaResult.equals(onnxResult) ) {
            return reportAndFail(report, opset, tfModel, "Diff between imported tf eval and onnx eval:\n\t" + vespaResult + "\n\t" + onnxResult);
        }

        return reportAndSucceed(report, opset, tfModel, "Ok");
    }

    private Pair<Integer, String> tf2onnxConvert(String savedModel, String output, int opset) throws IOException {
        ProcessExecuter executer = new ProcessExecuter();
        String job = "python3 -m tf2onnx.convert --saved-model " + savedModel + " --output " + output + " --opset " + opset;
        return executer.exec(job);
    }

    private static class Report {
        final ObjectMapper mapper = new ObjectMapper();
        final Map<String, ArrayNode> results = new HashMap<>();

        public boolean add(String model, int opset, boolean ok, String desc) {
            ObjectNode obj = mapper.createObjectNode().
                    put("opset", opset).
                    put("ok", ok).
                    put("desc", desc);
            results.computeIfAbsent(model, r -> mapper.createArrayNode()).add(obj);
            return ok;
        }

        public String toString() {
            ArrayNode array = mapper.createArrayNode();
            results.forEach((key, value) -> array.add(mapper.createObjectNode().
                    put("model", key).
                    set("tests", value)));
            try {
                return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(array);
            } catch (JsonProcessingException e) {
                return e.getMessage();
            }
        }
    }

    private static boolean reportAndFail(Report report, int opset, String model, String desc) {
        return report.add(model, opset, false, desc);
    }

    private static boolean reportAndSucceed(Report report, int opset, String model, String desc) {
        return report.add(model, opset, true, desc);
    }

}
