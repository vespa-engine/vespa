// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.onnxruntime.platform.Fp16Conversions;
import ai.vespa.llm.clients.TritonConfig;
import ai.vespa.rankingexpression.importer.onnx.OnnxImporter;
import com.google.protobuf.ByteString;
import com.yahoo.api.annotations.Beta;
import com.yahoo.component.annotation.Inject;
import com.yahoo.tensor.DimensionSizes;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.text.Text;
import com.yahoo.language.process.TimeoutException;
import com.yahoo.yolean.Exceptions;
import inference.GRPCInferenceServiceGrpc;
import inference.GrpcService;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Experimental model inference using Nvidia Triton as ONNX backend: model metadata and evaluation of tensors.
 * Loading and unloading models goes through {@link TritonModelRepositoryClient}.
 *
 * @author bjorncs
 * @author glebashnik
 */
@Beta
public class TritonOnnxClient implements AutoCloseable {

    private final GRPCInferenceServiceGrpc.GRPCInferenceServiceBlockingV2Stub grpcInferenceStub;
    private final Duration defaultTimeout;

    @Inject
    public TritonOnnxClient(TritonConfig config) {
        this.grpcInferenceStub = TritonGrpc.newStub(config);
        this.defaultTimeout = Duration.ofMillis(config.timeout());
    }

    public static class ModelMetadata {
        public final Map<String, TensorType> inputs;
        public final Map<String, TensorType> outputs;
        // Only used by `evaluate` inside the client, thus `private`.
        private final List<GrpcService.ModelMetadataResponse.TensorMetadata> tritonInputs;

        /** For tests of code which only reads the types */
        ModelMetadata(Map<String, TensorType> inputs, Map<String, TensorType> outputs) {
            this(inputs, outputs, List.of());
        }

        private ModelMetadata(
                Map<String, TensorType> inputs,
                Map<String, TensorType> outputs,
                List<GrpcService.ModelMetadataResponse.TensorMetadata> tritonInputs) {
            this.inputs = Collections.unmodifiableMap(inputs);
            this.outputs = Collections.unmodifiableMap(outputs);
            this.tritonInputs = Collections.unmodifiableList(tritonInputs);
        }
    }

    public ModelMetadata getModelMetadata(String modelName) {
        var request =
                GrpcService.ModelMetadataRequest.newBuilder().setName(modelName).build();
        var response = TritonGrpc.invoke(grpcInferenceStub.withDeadlineAfter(defaultTimeout),
                s -> s.modelMetadata(request), "Failed to get model metadata");
        var inputs = toTensorTypes(response.getInputsList());
        var outputs = toTensorTypes(response.getOutputsList());
        return new ModelMetadata(inputs, outputs, response.getInputsList());
    }

    /**
     * Returns whether the error is Triton's ONNX Runtime backend refusing to load a model with a non-zero max batch size,
     * since an input or output of the model has no dynamic first dimension.
     * Only Triton's auto-complete reports this error.
     * TritonOnnxRuntime specifies the inputs and outputs only in model configs with max batch size 0,
     * so the others need auto-complete.
     */
    public static boolean isBatchingRejection(TritonException e) {
        return Exceptions.toMessageString(e).contains("does not support batching while non-zero max_batch_size");
    }

    public Map<String, Tensor> evaluate(String modelName, ModelMetadata modelMetadata, Map<String, Tensor> inputs) {
        return evaluate(modelName, modelMetadata, inputs, Set.of(), Optional.empty());
    }

    public Map<String, Tensor> evaluate(
            String modelName, ModelMetadata modelMetadata, Map<String, Tensor> inputs, Optional<Instant> deadline) {
        return evaluate(modelName, modelMetadata, inputs, Set.of(), deadline);
    }

    public Tensor evaluate(
            String modelName, ModelMetadata modelMetadata, Map<String, Tensor> inputs, String outputName) {
        return evaluate(modelName, modelMetadata, inputs, Set.of(outputName), Optional.empty()).get(outputName);
    }

    public Tensor evaluate(
            String modelName, ModelMetadata modelMetadata, Map<String, Tensor> inputs, String outputName,
            Optional<Instant> deadline) {
        return evaluate(modelName, modelMetadata, inputs, Set.of(outputName), deadline).get(outputName);
    }

    public Map<String, Tensor> evaluate(
            String modelName, ModelMetadata modelMetadata, Map<String, Tensor> inputs, Set<String> outputNames) {
        return evaluate(modelName, modelMetadata, inputs, outputNames, Optional.empty());
    }

    /**
     * Evaluates the given outputs, or all outputs if none are given.
     *
     * @param deadline the deadline of the evaluation, or empty to use {@link TritonConfig#timeout()}
     * @throws TimeoutException if the deadline passes before Triton responds
     */
    public Map<String, Tensor> evaluate(
            String modelName, ModelMetadata modelMetadata, Map<String, Tensor> inputs, Set<String> outputNames,
            Optional<Instant> deadline) {
        Duration timeout = Objects.requireNonNull(deadline, "deadline")
                .map(instant -> Duration.between(Instant.now(), instant))
                .orElse(defaultTimeout);
        if (timeout.toMillis() <= 0) {
            throw new TimeoutException(
                    "Request deadline exceeded before Triton ONNX evaluation of model " + modelName);
        }

        var requestBuilder = GrpcService.ModelInferRequest.newBuilder().setModelName(modelName);

        inputs.forEach((name, tensor) -> addInputToBuilder(modelMetadata.tritonInputs, requestBuilder, tensor, name));

        // Returns all output if none is specified
        outputNames.forEach(
                name -> requestBuilder.addOutputs(GrpcService.ModelInferRequest.InferRequestedOutputTensor.newBuilder()
                        .setName(name)
                        .build()));

        var stub = grpcInferenceStub.withDeadlineAfter(timeout.toMillis(), MILLISECONDS);
        var response = TritonGrpc.invoke(stub, s -> s.modelInfer(requestBuilder.build()), "Failed to evaluate model");

        Map<String, Tensor> outputs = new HashMap<>();
        for (int i = 0; i < response.getOutputsCount(); i++) {
            var tritonTensor = response.getOutputs(i);
            var name = OnnxImporter.asValidIdentifier(tritonTensor.getName());
            var outputBuffer = ByteBuffer.wrap(response.getRawOutputContents(i).toByteArray())
                    .order(ByteOrder.LITTLE_ENDIAN);
            var tensor =
                    createTensorFromRawOutput(outputBuffer, tritonTensor.getDatatype(), tritonTensor.getShapeList());
            outputs.put(name, tensor);
        }

        return outputs;
    }

    @Override
    public void close() {
        TritonGrpc.close(grpcInferenceStub);
    }

    private static void addInputToBuilder(
            List<GrpcService.ModelMetadataResponse.TensorMetadata> onnxInputTypes,
            GrpcService.ModelInferRequest.Builder builder,
            Tensor vespaTensor,
            String vespaName) {
        if (!(vespaTensor instanceof IndexedTensor indexedTensor)) {
            throw new TritonException("Nvidia Triton currently only supports tensors with indexed dimensions");
        }
        var onnxInput = findMatchingInput(onnxInputTypes, vespaName);
        var inputBuilder = GrpcService.ModelInferRequest.InferInputTensor.newBuilder()
                .setName(onnxInput.getName())
                .setDatatype(onnxInput.getDatatype());
        for (long dim : indexedTensor.shape()) {
            inputBuilder.addShape(dim);
        }
        builder.addInputs(inputBuilder.build());
        builder.addRawInputContents(createRawInputContent(onnxInput, indexedTensor));
    }

    private static GrpcService.ModelMetadataResponse.TensorMetadata findMatchingInput(
            List<GrpcService.ModelMetadataResponse.TensorMetadata> onnxInputTypes, String vespaName) {
        for (var inputType : onnxInputTypes) {
            if (inputType.getName().equals(vespaName)) return inputType;
        }
        for (var inputType : onnxInputTypes) {
            if (OnnxImporter.asValidIdentifier(inputType.getName()).equals(vespaName)) return inputType;
        }
        throw new TritonException("No matching input type found for " + vespaName);
    }

    private static ByteString createRawInputContent(
            GrpcService.ModelMetadataResponse.TensorMetadata onnxInputType, IndexedTensor vespaTensor) {
        ByteBuffer buffer;
        String dataType = onnxInputType.getDatatype();
        int size = (int) vespaTensor.size();

        switch (dataType) {
            case "FP32" -> {
                buffer = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN);
                var floatBuffer = buffer.asFloatBuffer();
                for (int i = 0; i < size; i++) {
                    floatBuffer.put(vespaTensor.getFloat(i));
                }
            }
            case "FP64" -> {
                buffer = ByteBuffer.allocate(size * 8).order(ByteOrder.LITTLE_ENDIAN);
                var doubleBuffer = buffer.asDoubleBuffer();
                for (int i = 0; i < size; i++) {
                    doubleBuffer.put(vespaTensor.get(i));
                }
            }
            case "INT8" -> {
                buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i < size; i++) {
                    buffer.put((byte) vespaTensor.get(i));
                }
            }
            case "INT16" -> {
                buffer = ByteBuffer.allocate(size * 2).order(ByteOrder.LITTLE_ENDIAN);
                var shortBuffer = buffer.asShortBuffer();
                for (int i = 0; i < size; i++) {
                    shortBuffer.put((short) vespaTensor.get(i));
                }
            }
            case "INT32" -> {
                buffer = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN);
                var intBuffer = buffer.asIntBuffer();
                for (int i = 0; i < size; i++) {
                    intBuffer.put((int) vespaTensor.get(i));
                }
            }
            case "INT64" -> {
                buffer = ByteBuffer.allocate(size * 8).order(ByteOrder.LITTLE_ENDIAN);
                var longBuffer = buffer.asLongBuffer();
                for (int i = 0; i < size; i++) {
                    longBuffer.put((long) vespaTensor.get(i));
                }
            }
            case "BF16" -> {
                buffer = ByteBuffer.allocate(size * 2).order(ByteOrder.LITTLE_ENDIAN);
                var shortBuffer = buffer.asShortBuffer();
                for (int i = 0; i < size; i++) {
                    shortBuffer.put(Fp16Conversions.floatToBf16(vespaTensor.getFloat(i)));
                }
            }
            case "FP16" -> {
                buffer = ByteBuffer.allocate(size * 2).order(ByteOrder.LITTLE_ENDIAN);
                var shortBuffer = buffer.asShortBuffer();
                for (int i = 0; i < size; i++) {
                    shortBuffer.put(Fp16Conversions.floatToFp16(vespaTensor.getFloat(i)));
                }
            }
            default -> throw new TritonException("Unsupported tensor datatype from Triton: " + dataType);
        }
        return ByteString.copyFrom(buffer.rewind());
    }

    private Tensor createTensorFromRawOutput(ByteBuffer buffer, String tritonType, List<Long> shape) {
        var vespaType = toVespaTensorType(tritonType, shape);
        var sizes = DimensionSizes.of(vespaType);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        var size = sizes.totalSize();
        var builder = (IndexedTensor.BoundBuilder) Tensor.Builder.of(vespaType, sizes);

        switch (tritonType) {
            case "BF16" -> {
                var shortBuffer = buffer.asShortBuffer();
                for (int i = 0; i < size; i++) {
                    builder.cellByDirectIndex(i, Fp16Conversions.bf16ToFloat(shortBuffer.get(i)));
                }
            }
            case "FP16" -> {
                var shortBuffer = buffer.asShortBuffer();
                for (int i = 0; i < size; i++) {
                    builder.cellByDirectIndex(i, Fp16Conversions.fp16ToFloat(shortBuffer.get(i)));
                }
            }
            case "FP32" -> {
                var floatBuffer = buffer.asFloatBuffer();
                for (int i = 0; i < size; i++) {
                    builder.cellByDirectIndex(i, floatBuffer.get(i));
                }
            }
            case "FP64" -> {
                var doubleBuffer = buffer.asDoubleBuffer();
                for (int i = 0; i < size; i++) {
                    builder.cellByDirectIndex(i, doubleBuffer.get(i));
                }
            }
            case "INT8" -> {
                for (int i = 0; i < size; i++) {
                    builder.cellByDirectIndex(i, buffer.get(i));
                }
            }
            case "INT16" -> {
                var shortBuffer = buffer.asShortBuffer();
                for (int i = 0; i < size; i++) {
                    builder.cellByDirectIndex(i, shortBuffer.get(i));
                }
            }
            case "INT32" -> {
                var intBuffer = buffer.asIntBuffer();
                for (int i = 0; i < size; i++) {
                    builder.cellByDirectIndex(i, intBuffer.get(i));
                }
            }
            case "INT64" -> {
                var longBuffer = buffer.asLongBuffer();
                for (int i = 0; i < size; i++) {
                    builder.cellByDirectIndex(i, longBuffer.get(i));
                }
            }
            default -> throw new TritonException(Text.format("Unsupported type from ONNX output: %s", tritonType));
        }
        return builder.build();
    }

    /** Converts {@link TensorType} using mapping rule similar to TensorConverter */
    private static Map<String, TensorType> toTensorTypes(
            Collection<GrpcService.ModelMetadataResponse.TensorMetadata> list) {
        return list.stream()
                .collect(Collectors.toMap(
                        tm -> OnnxImporter.asValidIdentifier(tm.getName()),
                        tm -> toVespaTensorType(tm.getDatatype(), tm.getShapeList())));
    }

    private static TensorType toVespaTensorType(String tritonType, List<Long> shapes) {
        var dataType =
                switch (tritonType) {
                    case "INT8" -> TensorType.Value.INT8;
                    case "BF16" -> TensorType.Value.BFLOAT16;
                    case "FP16", "FP32" -> TensorType.Value.FLOAT;
                    default -> TensorType.Value.DOUBLE;
                };
        var builder = new TensorType.Builder(dataType);
        for (int i = 0; i < shapes.size(); i++) {
            long shape = shapes.get(i);
            String dimName = "d" + i; // Using index instead of shape value as in TensorConverter
            if (shape >= 0) {
                builder.indexed(dimName, shape);
            } else {
                builder.indexed(dimName);
            }
        }
        return builder.build();
    }

}
