// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.rankingexpression.importer.onnx;

import ai.vespa.rankingexpression.importer.ImportedModel;
import ai.vespa.rankingexpression.importer.IntermediateGraph;
import ai.vespa.rankingexpression.importer.ModelImporter;
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
            IntermediateGraph graph = GraphImporter.importGraph(modelName, model);
            return convertIntermediateGraphToModel(graph, modelPath);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not import ONNX model from '" + modelPath + "'", e);
        }
    }

}
