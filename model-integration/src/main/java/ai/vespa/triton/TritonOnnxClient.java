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
import grpc.health.v1.HealthGrpc;
import grpc.health.v1.HealthOuterClass.HealthCheckRequest;
import grpc.health.v1.HealthOuterClass.HealthCheckResponse;
import inference.GRPCInferenceServiceGrpc;
import inference.GrpcService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.AbstractBlockingStub;
import io.grpc.stub.AbstractStub;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Experimental model inference using Nvidia Triton as ONNX backend.
 *
 * @author bjorncs
 */
@Beta
public class TritonOnnxClient implements AutoCloseable {

    private static final Logger log = Logger.getLogger(TritonOnnxClient.class.getName());

    private final GRPCInferenceServiceGrpc.GRPCInferenceServiceBlockingV2Stub grpcInferenceStub;
    private final HealthGrpc.HealthBlockingV2Stub grpcHealthStub;

    public static class TritonException extends RuntimeException {
        public TritonException(String message) { super(message); }
        public TritonException(String message, Throwable cause) { super(message, cause); }
    }

    @Inject
    public TritonOnnxClient(TritonConfig config) {
        var ch = ManagedChannelBuilder.forTarget(config.target())
                .usePlaintext()
                .build();
        this.grpcInferenceStub = GRPCInferenceServiceGrpc.newBlockingV2Stub(ch);
        this.grpcHealthStub = HealthGrpc.newBlockingV2Stub(ch);
    }

    public record ModelMetadata(Map<String, TensorType> inputs, Map<String, TensorType> outputs) {}
    public ModelMetadata getModelMetadata(String modelName) {
        var request = GrpcService.ModelMetadataRequest.newBuilder()
                .setName(modelName)
                .build();
        var response = invokeGrpc(
                grpcInferenceStub, s -> s.modelMetadata(request), "Failed to get model metadata " + modelName);
        var inputs = toTensorTypes(response.getInputsList());
        var outputs = toTensorTypes(response.getOutputsList());
        return new ModelMetadata(inputs, outputs);
    }


    public boolean isHealthy() {
        var req = HealthCheckRequest.newBuilder().build();
        var response = invokeGrpc(grpcHealthStub, s -> s.check(req), "Failed to check health");
        log.fine(() -> "Triton health status: " + response.getStatus());
        return response.getStatus() == HealthCheckResponse.ServingStatus.SERVING;
    }

    public void loadModel(String modelName) {
        log.fine(() -> "Loading model " + modelName);
        var request = GrpcService.RepositoryModelLoadRequest.newBuilder()
                .setModelName(modelName)
                .build();
        invokeGrpc(grpcInferenceStub, s -> s.repositoryModelLoad(request), "Failed to load model: " + modelName);
    }

    public void unloadModel(String modelName) {
        var request = GrpcService.RepositoryModelUnloadRequest.newBuilder()
                .setModelName(modelName)
                .build();
        invokeGrpc(grpcInferenceStub, s -> s.repositoryModelUnload(request), "Failed to unload model: " + modelName);
    }

    public Map<String, Tensor> evaluate(String modelName, Map<String, Tensor> inputs) {
        return evaluate(modelName, inputs, Set.of());
    }

    public Tensor evaluate(String modelName, Map<String, Tensor> inputs, String outputName) {
        return evaluate(modelName, inputs, Set.of(outputName)).get(outputName);
    }

    public Map<String, Tensor> evaluate(String modelName, Map<String, Tensor> inputs, Set<String> outputNames) {
        var requestBuilder = GrpcService.ModelInferRequest.newBuilder()
                .setModelName(modelName);

        // Get model metadata to convert vespa tensor types to onnx types
        var metadata = invokeGrpc(
                grpcInferenceStub, s -> s.modelMetadata(
                        GrpcService.ModelMetadataRequest.newBuilder()
                                .setName(modelName)
                                .build()), "Failed to get model metadata: " + modelName
        );

        inputs.forEach((name, tensor) -> addInputToBuilder(metadata.getInputsList(), requestBuilder, tensor, name));

        // Returns all output if none is specified
        outputNames.forEach(name -> requestBuilder.addOutputs(
                GrpcService.ModelInferRequest.InferRequestedOutputTensor.newBuilder()
                        .setName(name)
                        .build()));

        var response = invokeGrpc(
                grpcInferenceStub, s -> s.modelInfer(requestBuilder.build()), "Failed to infer model: " + modelName);

        Map<String, Tensor> outputs = new HashMap<>();
        for (int i = 0; i < response.getOutputsCount(); i++) {
            var tritonTensor = response.getOutputs(i);
            var name = OnnxImporter.asValidIdentifier(tritonTensor.getName());
            var outputBuffer =
                    ByteBuffer.wrap(response.getRawOutputContents(i).toByteArray())
                            .order(ByteOrder.LITTLE_ENDIAN);
            var tensor = createTensorFromRawOutput(outputBuffer, tritonTensor.getDatatype(), tritonTensor.getShapeList());
            outputs.put(name, tensor);
        }

        return outputs;
    }

    @Override
    public void close() {
        var ch = (ManagedChannel) invokeGrpc(grpcInferenceStub, AbstractStub::getChannel, "Failed to get channel");
        ch.shutdown();
        try {
            if (!ch.awaitTermination(5, SECONDS))
                throw new IllegalStateException("Failed to close channel");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TritonException("Failed to close channel", e);
        } finally {
            ch.shutdownNow();
        }
    }

    private static void addInputToBuilder(List<GrpcService.ModelMetadataResponse.TensorMetadata> onnxInputTypes,
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
            default -> throw new TritonException("Unsupported type from ONNX output: %s".formatted(tritonType));
        }
        return builder.build();
    }

    /** Converts {@link TensorType} using mapping rule similar to TensorConverter */
    private static Map<String, TensorType> toTensorTypes(Collection<GrpcService.ModelMetadataResponse.TensorMetadata> list) {
        return list.stream()
                .collect(Collectors.toMap(
                        tm -> OnnxImporter.asValidIdentifier(tm.getName()),
                        tm -> toVespaTensorType(tm.getDatatype(), tm.getShapeList())
                ));
    }

    private static TensorType toVespaTensorType(String tritonType, List<Long> shapes) {
        var dataType = switch (tritonType) {
            case "INT8" -> TensorType.Value.INT8;
            case "BF16" -> TensorType.Value.BFLOAT16;
            case "FP16", "FP32" -> TensorType.Value.FLOAT;
            default -> TensorType.Value.DOUBLE;
        };
        var builder = new TensorType.Builder(dataType);
        for (int i = 0; i < shapes.size(); i++) {
            long shape = shapes.get(i);
            String dimName = "d" + i;  // Using index instead of shape value as in TensorConverter
            if (shape >= 0) {
                builder.indexed(dimName, shape);
            } else {
                builder.indexed(dimName);
            }
        }
        return builder.build();
    }


    // Converts StatusRuntimeException to TritonException
    private <T, S extends AbstractBlockingStub<S>> T invokeGrpc(S stub, Function<S, T> invocation, String errorMessage) {
        try {
            return invocation.apply(stub);
        } catch (StatusRuntimeException e) {
            throw new TritonException(errorMessage, e);
        }
    }
}
