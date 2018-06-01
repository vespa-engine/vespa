// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.searchlib.rankingexpression.integration.ml;

import com.yahoo.searchlib.rankingexpression.integration.ml.importer.IntermediateGraph;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.onnx.GraphImporter;
import onnx.Onnx;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Converts a ONNX model into a ranking expression and set of constants.
 *
 * @author lesters
 */
public class OnnxImporter extends ModelImporter {

    private static final Logger log = Logger.getLogger(OnnxImporter.class.getName());

    @Override
    public ImportedModel importModel(String modelName, String modelPath) {
        try (FileInputStream inputStream = new FileInputStream(modelPath)) {
            Onnx.ModelProto model = Onnx.ModelProto.parseFrom(inputStream);
            IntermediateGraph graph = GraphImporter.importGraph(modelName, model);
            return convertIntermediateGraphToModel(graph);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not import ONNX model from '" + modelPath + "'", e);
        }
    }

}
