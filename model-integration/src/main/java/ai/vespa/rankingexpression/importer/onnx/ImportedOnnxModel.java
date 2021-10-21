// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.onnx;

import ai.vespa.rankingexpression.importer.ImportedModel;
import onnx.Onnx;

public class ImportedOnnxModel extends ImportedModel {

    private final Onnx.ModelProto modelProto;

    public ImportedOnnxModel(String name, String source, Onnx.ModelProto modelProto) {
        super(name, source, ModelType.ONNX);
        this.modelProto = modelProto;
    }

    @Override
    public boolean isNative() {
        return false;
    }

    @Override
    public ImportedModel asNative() {
        return OnnxImporter.convertModel(name(), source(), modelProto, ModelType.ONNX);
    }
}
