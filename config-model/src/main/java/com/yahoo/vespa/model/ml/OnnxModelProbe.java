package com.yahoo.vespa.model.ml;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.tensor.TensorType;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Defers to 'vespa-analyze-onnx-model' to determine the output type given
 * a set of inputs. For situations with symbolic dimension sizes that can't
 * easily be determined.
 *
 * @author lesters
 */
public class OnnxModelProbe {

    private static final String binary = "vespa-analyze-onnx-model";

    static TensorType probeModel(ApplicationPackage app, Path modelPath, String outputName, Map<String, TensorType> inputTypes) {
        TensorType outputType = TensorType.empty;
        String contextKey = createContextKey(outputName, inputTypes);

        try {
            // Check if output type has already been probed
            outputType = readProbedOutputType(app, modelPath, contextKey);

            // Otherwise, run vespa-analyze-onnx-model if the model is available
            if (outputType.equals(TensorType.empty) && app.getFile(modelPath).exists()) {
                String jsonInput = createJsonInput(app.getFileReference(modelPath).getAbsolutePath(), inputTypes);
                String jsonOutput = callVespaAnalyzeOnnxModel(jsonInput);
                outputType = outputTypeFromJson(jsonOutput, outputName);
                if ( ! outputType.equals(TensorType.empty)) {
                    writeProbedOutputType(app, modelPath, contextKey, outputType);
                }
            }

        } catch (IllegalArgumentException | IOException | InterruptedException ignored) { }

        return outputType;
    }

    private static String createContextKey(String onnxName, Map<String, TensorType> inputTypes) {
        StringBuilder key = new StringBuilder().append(onnxName).append(":");
        inputTypes.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEachOrdered(e -> key.append(e.getKey()).append(":").append(e.getValue()).append(","));
        return key.substring(0, key.length()-1);
    }

    private static Path probedOutputTypesPath(Path path) {
        String fileName = OnnxModelInfo.asValidIdentifier(path.getRelative()) + ".probed_output_types";
        return ApplicationPackage.MODELS_GENERATED_REPLICATED_DIR.append(fileName);
    }

    static void writeProbedOutputType(ApplicationPackage app, Path modelPath, String output,
                                      Map<String, TensorType> inputTypes, TensorType type) throws IOException {
        writeProbedOutputType(app, modelPath, createContextKey(output, inputTypes), type);
    }

    private static void writeProbedOutputType(ApplicationPackage app, Path modelPath,
                                              String contextKey, TensorType type) throws IOException {
        String path = app.getFileReference(probedOutputTypesPath(modelPath)).getAbsolutePath();
        IOUtils.writeFile(path, contextKey + "\t" + type + "\n", true);
    }

    private static TensorType readProbedOutputType(ApplicationPackage app, Path modelPath,
                                                   String contextKey) throws IOException {
        ApplicationFile file = app.getFile(probedOutputTypesPath(modelPath));
        if ( ! file.exists()) {
            return TensorType.empty;
        }
        try (BufferedReader reader = new BufferedReader(file.createReader())) {
            String line;
            while (null != (line = reader.readLine())) {
                String[] parts = line.split("\t");
                String key = parts[0];
                if (key.equals(contextKey)) {
                    return TensorType.fromSpec(parts[1]);
                }
            }
        }
        return TensorType.empty;
    }

    private static TensorType outputTypeFromJson(String json, String outputName) throws IOException {
        ObjectMapper m = new ObjectMapper();
        JsonNode root = m.readTree(json);
        if ( ! root.isObject() || ! root.has("outputs")) {
            return TensorType.empty;
        }
        JsonNode outputs = root.get("outputs");
        if ( ! outputs.has(outputName)) {
            return TensorType.empty;
        }
        return TensorType.fromSpec(outputs.get(outputName).asText());
    }

    private static String createJsonInput(String modelPath, Map<String, TensorType> inputTypes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator g = new JsonFactory().createGenerator(out, JsonEncoding.UTF8);
        g.writeStartObject();
        g.writeStringField("model", modelPath);
        g.writeObjectFieldStart("inputs");
        for (Map.Entry<String, TensorType> input : inputTypes.entrySet()) {
            g.writeStringField(input.getKey(), input.getValue().toString());
        }
        g.writeEndObject();
        g.writeEndObject();
        g.close();
        return out.toString();
    }

    private static String callVespaAnalyzeOnnxModel(String jsonInput) throws IOException, InterruptedException {
        StringBuilder output = new StringBuilder();

        ProcessBuilder processBuilder = new ProcessBuilder(binary, "--probe-types");
        processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process process = processBuilder.start();

        // Write json array to process stdin
        OutputStream os = process.getOutputStream();
        os.write(jsonInput.getBytes(StandardCharsets.UTF_8));
        os.close();

        // Read output from stdout
        InputStream inputStream = process.getInputStream();
        while (true) {
            int b = inputStream.read();
            if (b == -1) break;
            output.append((char)b);
        }

        int returnCode = process.waitFor();
        if (returnCode != 0) {
            throw new IllegalArgumentException("Error from '" + binary + "'. Return code: " + returnCode + ". " +
                                               "Output: '" + output + "'");
        }
        return output.toString();
    }

}
