// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.modelintegration.evaluator;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxJavaType;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import ai.onnxruntime.ValueInfo;
import ai.onnxruntime.platform.Fp16Conversions;
import ai.vespa.rankingexpression.importer.onnx.OnnxImporter;
import com.yahoo.tensor.DimensionSizes;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * @author lesters
 */
class TensorConverter {

    static Map<String, OnnxTensor> toOnnxTensors(Map<String, Tensor> tensorMap, OrtEnvironment env, OrtSession session)
        throws OrtException
    {
        Map<String, OnnxTensor> result = new HashMap<>();
        for (String name : tensorMap.keySet()) {
            Tensor vespaTensor = tensorMap.get(name);
            name = toOnnxName(name, session.getInputInfo().keySet());
            TensorInfo onnxTensorInfo = toTensorInfo(session.getInputInfo().get(name).getInfo());
            OnnxTensor onnxTensor = toOnnxTensor(vespaTensor, onnxTensorInfo, env);
            result.put(name, onnxTensor);
        }
        return result;
    }

    static OnnxTensor toOnnxTensor(Tensor vespaTensor, TensorInfo onnxTensorInfo, OrtEnvironment environment)
        throws OrtException
    {
        if ( ! (vespaTensor instanceof IndexedTensor)) {
            throw new IllegalArgumentException("OnnxEvaluator currently only supports tensors with indexed dimensions");
        }
        IndexedTensor tensor = (IndexedTensor) vespaTensor;
        ByteBuffer buffer = ByteBuffer.allocateDirect((int)tensor.size() * onnxTensorInfo.type.size).order(ByteOrder.nativeOrder());
        if (onnxTensorInfo.type == OnnxJavaType.FLOAT) {
            for (int i = 0; i < tensor.size(); i++)
                buffer.putFloat(tensor.getFloat(i));
            return OnnxTensor.createTensor(environment, buffer.rewind().asFloatBuffer(), tensor.shape());
        }
        if (onnxTensorInfo.type == OnnxJavaType.DOUBLE) {
            for (int i = 0; i < tensor.size(); i++)
                buffer.putDouble(tensor.get(i));
            return OnnxTensor.createTensor(environment, buffer.rewind().asDoubleBuffer(), tensor.shape());
        }
        if (onnxTensorInfo.type == OnnxJavaType.INT8) {
            for (int i = 0; i < tensor.size(); i++)
                buffer.put((byte) tensor.get(i));
            return OnnxTensor.createTensor(environment, buffer.rewind(), tensor.shape());
        }
        if (onnxTensorInfo.type == OnnxJavaType.INT16) {
            for (int i = 0; i < tensor.size(); i++)
                buffer.putShort((short) tensor.get(i));
            return OnnxTensor.createTensor(environment, buffer.rewind().asShortBuffer(), tensor.shape());
        }
        if (onnxTensorInfo.type == OnnxJavaType.INT32) {
            for (int i = 0; i < tensor.size(); i++)
                buffer.putInt((int) tensor.get(i));
            return OnnxTensor.createTensor(environment, buffer.rewind().asIntBuffer(), tensor.shape());
        }
        if (onnxTensorInfo.type == OnnxJavaType.INT64) {
            for (int i = 0; i < tensor.size(); i++)
                buffer.putLong((long) tensor.get(i));
            return OnnxTensor.createTensor(environment, buffer.rewind().asLongBuffer(), tensor.shape());
        }
        if (onnxTensorInfo.type == OnnxJavaType.FLOAT16) {
            for (int i = 0; i < tensor.size(); i++) {
                buffer.putShort(Fp16Conversions.floatToFp16((float)tensor.get(i)));
            }
            return OnnxTensor.createTensor(environment, buffer.rewind(), tensor.shape(), OnnxJavaType.FLOAT16);
        }
        if (onnxTensorInfo.type == OnnxJavaType.BFLOAT16) {
            for (int i = 0; i < tensor.size(); i++)
                buffer.putShort(Fp16Conversions.floatToBf16((float)tensor.get(i)));
            return OnnxTensor.createTensor(environment, buffer.rewind(), tensor.shape(), OnnxJavaType.BFLOAT16);
        }
        throw new IllegalArgumentException("OnnxEvaluator does not currently support value type " + onnxTensorInfo.type);
    }

    static Tensor toVespaTensor(OnnxValue onnxValue) {
        if ( ! (onnxValue instanceof OnnxTensor)) {
            throw new IllegalArgumentException("ONNX value is not a tensor: maps and sequences are not yet supported");
        }
        OnnxTensor onnxTensor = (OnnxTensor) onnxValue;
        TensorInfo tensorInfo = onnxTensor.getInfo();

        TensorType type = toVespaType(onnxTensor.getInfo());
        DimensionSizes sizes = sizesFromType(type);

        IndexedTensor.BoundBuilder builder = (IndexedTensor.BoundBuilder) Tensor.Builder.of(type, sizes);
        if (tensorInfo.type == OnnxJavaType.FLOAT) {
            FloatBuffer buffer = onnxTensor.getFloatBuffer();
            for (long i = 0; i < sizes.totalSize(); i++)
                builder.cellByDirectIndex(i, buffer.get());
        }
        else if (tensorInfo.type == OnnxJavaType.DOUBLE) {
            DoubleBuffer buffer = onnxTensor.getDoubleBuffer();
            for (long i = 0; i < sizes.totalSize(); i++)
                builder.cellByDirectIndex(i, buffer.get());
        }
        else if (tensorInfo.type == OnnxJavaType.INT8) {
            ByteBuffer buffer = onnxTensor.getByteBuffer();
            for (long i = 0; i < sizes.totalSize(); i++)
                builder.cellByDirectIndex(i, buffer.get());
        }
        else if (tensorInfo.type == OnnxJavaType.INT16) {
            ShortBuffer buffer = onnxTensor.getShortBuffer();
            for (long i = 0; i < sizes.totalSize(); i++)
                builder.cellByDirectIndex(i, buffer.get());
        }
        else if (tensorInfo.type == OnnxJavaType.INT32) {
            IntBuffer buffer = onnxTensor.getIntBuffer();
            for (long i = 0; i < sizes.totalSize(); i++)
                builder.cellByDirectIndex(i, buffer.get());
        }
        else if (tensorInfo.type == OnnxJavaType.INT64) {
            LongBuffer buffer = onnxTensor.getLongBuffer();
            for (long i = 0; i < sizes.totalSize(); i++)
                builder.cellByDirectIndex(i, buffer.get());
        }
        else if (tensorInfo.type == OnnxJavaType.FLOAT16) {
            ShortBuffer buffer = onnxTensor.getShortBuffer();
            for (long i = 0; i < sizes.totalSize(); i++)
                builder.cellByDirectIndex(i, Fp16Conversions.fp16ToFloat(buffer.get()));
        }
        else if (tensorInfo.type == OnnxJavaType.BFLOAT16) {
            ShortBuffer buffer = onnxTensor.getShortBuffer();
            for (long i = 0; i < sizes.totalSize(); i++)
                builder.cellByDirectIndex(i, Fp16Conversions.bf16ToFloat((buffer.get())));
        }
        else {
            throw new IllegalArgumentException("OnnxEvaluator does not currently support value type " + onnxTensor.getInfo().type);
        }
        return builder.build();
    }

    static private DimensionSizes sizesFromType(TensorType type) {
        DimensionSizes.Builder builder = new DimensionSizes.Builder(type.dimensions().size());
        for (int i = 0; i < type.dimensions().size(); i++)
            builder.set(i, type.dimensions().get(i).size().get());
        return builder.build();
    }

    static Map<String, TensorType> toVespaTypes(Map<String, NodeInfo> infoMap) {
        return infoMap.entrySet().stream().collect(Collectors.toMap(e -> asValidName(e.getKey()),
                                                                    e -> toVespaType(e.getValue().getInfo())));
    }

    static String asValidName(String name) {
        return OnnxImporter.asValidIdentifier(name);
    }

    static String toOnnxName(String name, Set<String> onnxNames) {
        if (onnxNames.contains(name))
            return name;
        for (String onnxName : onnxNames) {
            if (asValidName(onnxName).equals(name))
                return onnxName;
        }
        throw new IllegalArgumentException("ONNX model has no input with name " + name);
    }

    static TensorType toVespaType(ValueInfo valueInfo) {
        TensorInfo tensorInfo = toTensorInfo(valueInfo);
        TensorType.Builder builder = new TensorType.Builder(toVespaValueType(tensorInfo.onnxType));
        long[] shape = tensorInfo.getShape();
        for (int i = 0; i < shape.length; ++i) {
            long dimSize = shape[i];
            String dimName = "d" + i;  // standard naming convention
            if (dimSize > 0)
                builder.indexed(dimName, dimSize);
            else
                builder.indexed(dimName);  // unbound dimension for dim size -1
        }
        return builder.build();
    }

    static private TensorType.Value toVespaValueType(TensorInfo.OnnxTensorType onnxType) {
        switch (onnxType) {
            case ONNX_TENSOR_ELEMENT_DATA_TYPE_INT8: return TensorType.Value.INT8;
            case ONNX_TENSOR_ELEMENT_DATA_TYPE_BFLOAT16: return TensorType.Value.BFLOAT16;
            case ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT16: return TensorType.Value.FLOAT;
            case ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT: return TensorType.Value.FLOAT;
            case ONNX_TENSOR_ELEMENT_DATA_TYPE_DOUBLE: return TensorType.Value.DOUBLE;
        }
        return TensorType.Value.DOUBLE;
    }

    static private TensorInfo toTensorInfo(ValueInfo valueInfo) {
        if ( ! (valueInfo instanceof TensorInfo)) {
            throw new IllegalArgumentException("ONNX value is not a tensor: maps and sequences are not yet supported");
        }
        return (TensorInfo) valueInfo;
    }

}
