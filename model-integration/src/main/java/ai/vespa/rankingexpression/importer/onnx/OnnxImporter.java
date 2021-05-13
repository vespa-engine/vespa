// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.rankingexpression.importer.onnx;

import ai.vespa.rankingexpression.importer.ImportedModel;
import ai.vespa.rankingexpression.importer.IntermediateGraph;
import ai.vespa.rankingexpression.importer.ModelImporter;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import onnx.Onnx;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

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

            // Todo: what to do about onnx_vespa?
            // IntermediateGraph graph = GraphImporter.importGraph(modelName, model);
            // return convertIntermediateGraphToModel(graph, modelPath);

            ImportedModel importedModel = new ImportedModel(modelName, modelPath);

            // don't need this actually...

            // Add inputs
//            for (Onnx.ValueInfoProto valueInfo : model.getGraph().getInputList()) {
//                String name = valueInfo.getName();
//                TensorType.Value valueType = toVespaType(valueInfo.getType().getTensorType().getElemType());
//                TensorType.Builder builder = new TensorType.Builder(valueType);
//                List<Onnx.TensorShapeProto.Dimension> onnxDimList = valueInfo.getType().getTensorType().getShape().getDimList();
//                for (int i = 0; i < onnxDimList.size(); ++ i) {
//                    Onnx.TensorShapeProto.Dimension dim = onnxDimList.get(i);
//                    if (dim.hasDimParam()) {
//                        builder.indexed("d" + i);  // TODO: is this correct?
//                    } else {
//                        builder.indexed("d" + i, dim.getDimValue());
//                    }
//                }
//                importedModel.input(name, builder.build());
//            }

            // what about different outputs?
            importedModel.expression("output", "onnx(\"" + modelName + "\")");
            return importedModel;

        } catch (IOException e) {
            throw new IllegalArgumentException("Could not import ONNX model from '" + modelPath + "'", e);
        }
    }

    private static TensorType.Value toVespaType(Onnx.TensorProto.DataType dataType) {
        switch (dataType) {
            case INT8: return TensorType.Value.INT8;
            case FLOAT: return TensorType.Value.FLOAT;
            case DOUBLE: return TensorType.Value.DOUBLE;

            // Imperfect conversion, for now:
            case BOOL: return TensorType.Value.FLOAT;
            case INT16: return TensorType.Value.FLOAT;
            case INT32: return TensorType.Value.FLOAT;
            case INT64: return TensorType.Value.DOUBLE;
            case UINT8: return TensorType.Value.FLOAT;
            case UINT16: return TensorType.Value.FLOAT;
            case UINT32: return TensorType.Value.FLOAT;
            case UINT64: return TensorType.Value.DOUBLE;
            default:
                throw new IllegalArgumentException("An ONNX tensor with data type " + dataType +
                        " cannot be converted to a Vespa tensor type");
        }
    }

}
