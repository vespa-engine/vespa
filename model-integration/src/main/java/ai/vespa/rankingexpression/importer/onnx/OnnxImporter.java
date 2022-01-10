// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.rankingexpression.importer.onnx;

import ai.vespa.rankingexpression.importer.ImportedModel;
import ai.vespa.rankingexpression.importer.IntermediateGraph;
import ai.vespa.rankingexpression.importer.ModelImporter;
import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModel;
import onnx.Onnx;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Converts a ONNX model into a ranking expression and set of constants.
 *
 * @author lesters
 */
public class OnnxImporter extends ModelImporter {

    @Override
    public boolean canImport(String modelPath) {
        File modelFile = new File(modelPath);
        if ( ! modelFile.isFile()) return false;

        return modelFile.toString().endsWith(".onnx");
    }

    @Override
    public ImportedModel importModel(String modelName, String modelPath) {
        try (FileInputStream inputStream = new FileInputStream(modelPath)) {
            Onnx.ModelProto model = Onnx.ModelProto.parseFrom(inputStream);
            // long version = model.getOpsetImport(0).getVersion();  // opset version

            ImportedModel importedModel = new ImportedOnnxModel(modelName, modelPath, model);
            for (int i = 0; i < model.getGraph().getOutputCount(); ++i) {
                Onnx.ValueInfoProto output = model.getGraph().getOutput(i);
                String outputName = asValidIdentifier(output.getName());
                importedModel.expression(outputName, "onnx(" + modelName + ")." + outputName);
            }
            return importedModel;

        } catch (IOException e) {
            throw new IllegalArgumentException("Could not import ONNX model from '" + modelPath + "'", e);
        }
    }

    public ImportedModel importModelAsNative(String modelName, String modelPath, ImportedMlModel.ModelType modelType) {
        try (FileInputStream inputStream = new FileInputStream(modelPath)) {
            Onnx.ModelProto model = Onnx.ModelProto.parseFrom(inputStream);
            return convertModel(modelName, modelPath, model, modelType);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not import ONNX model from '" + modelPath + "'", e);
        }
    }

    public static String asValidIdentifier(String str) {
        return str.replaceAll("[^\\w\\d\\$@_]", "_");
    }

    static ImportedModel convertModel(String name, String source, Onnx.ModelProto modelProto, ImportedMlModel.ModelType modelType) {
         IntermediateGraph graph = GraphImporter.importGraph(name, modelProto);
         return convertIntermediateGraphToModel(graph, source, modelType);
    }

}
