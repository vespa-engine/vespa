// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import com.google.protobuf.ByteString;
import inference.ModelConfigOuterClass.ModelConfig;
import onnx.Onnx;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Discovers CPU or GPU placement for Triton's ONNX backend, using a temporary KIND_AUTO model.
 * Returns the selected GPU IDs, or an empty set when Triton selects CPU. Other device kinds are not supported.
 * Requires EXPLICIT model control. The result is a snapshot for the lifetime of the runtime.
 * A CPU result does not distinguish missing GPUs from drivers Triton considers unavailable.
 */
final class TritonGpuProbe {
    private static final Logger log = Logger.getLogger(TritonGpuProbe.class.getName());
    private static final String MODEL_NAME = "vespa_device_probe";
    private static final ByteString MODEL = createModel();

    private final TritonOnnxClient client;
    private Set<Integer> gpuDevices;

    TritonGpuProbe(TritonOnnxClient client) {
        this.client = client;
    }

    // Serialize first use, including cleanup. Failed probes are not cached as "no GPU".
    synchronized Set<Integer> gpuDevices() {
        if (gpuDevices == null) {
            gpuDevices = probe();
            log.info(() -> "Triton ONNX GPU devices: " + gpuDevices);
        }
        return gpuDevices;
    }

    private Set<Integer> probe() {
        var modelName = MODEL_NAME + "_" + UUID.randomUUID().toString().replace("-", "");
        RuntimeException failure = null;
        try {
            client.loadModel(modelName, configJson(modelName), MODEL);
            return gpuDevices(client.getModelConfig(modelName));
        } catch (RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            try {
                // Always send unload, including after an ambiguous load timeout. Inline models need not
                // appear in the filesystem repository index used by unloadUntilModelNotReady().
                client.unloadModel(modelName);
                client.unloadUntilModelNotReady(modelName);
            } catch (RuntimeException e) {
                if (failure == null) throw e;
                failure.addSuppressed(e);
            }
        }
    }

    private static Set<Integer> gpuDevices(ModelConfig config) {
        if (config.getInstanceGroupCount() == 0) {
            throw new TritonOnnxClient.TritonException("Device probe returned no instance groups");
        }
        var devices = new HashSet<Integer>();
        for (var group : config.getInstanceGroupList()) {
            switch (group.getKind()) {
                case KIND_GPU -> {
                    if (group.getGpusCount() == 0 || group.getGpusList().stream().anyMatch(id -> id < 0)) {
                        throw new TritonOnnxClient.TritonException("Device probe returned invalid GPU devices: " + group);
                    }
                    devices.addAll(group.getGpusList());
                }
                case KIND_CPU -> {
                    if (group.getGpusCount() != 0) {
                        throw new TritonOnnxClient.TritonException("Device probe returned CPU instances with GPUs: " + group);
                    }
                }
                default -> throw new TritonOnnxClient.TritonException("Device probe returned unresolved device kind: " + group);
            }
        }
        return Set.copyOf(devices);
    }

    // Explicit tensor metadata avoids backend auto-completion, which can itself select a device.
    // Leaving gpus unset lets Triton expand KIND_AUTO to all supported GPUs, or CPU if none exist.
    static String configJson(String modelName) {
        return """
                {
                  "name": "%s",
                  "platform": "onnxruntime_onnx",
                  "max_batch_size": 0,
                  "input": [{"name": "input", "data_type": "TYPE_FP32", "dims": [1]}],
                  "output": [{"name": "output", "data_type": "TYPE_FP32", "dims": [1]}],
                  "instance_group": [{"kind": "KIND_AUTO", "count": 1}],
                  "parameters": {
                    "intra_op_thread_count": {"string_value": "1"},
                    "inter_op_thread_count": {"string_value": "1"}
                  }
                }
                """.formatted(modelName);
    }

    // A single Add node, output = input + input. Built with the existing ONNX protobuf dependency
    // so there is no opaque binary asset or Python/ONNX tooling required at build time.
    static ByteString createModel() {
        var type = Onnx.TypeProto.newBuilder().setTensorType(Onnx.TypeProto.Tensor.newBuilder()
                .setElemType(Onnx.TensorProto.DataType.FLOAT.getNumber())
                .setShape(Onnx.TensorShapeProto.newBuilder()
                        .addDim(Onnx.TensorShapeProto.Dimension.newBuilder().setDimValue(1))));
        return Onnx.ModelProto.newBuilder()
                .setIrVersion(8)
                .addOpsetImport(Onnx.OperatorSetIdProto.newBuilder().setVersion(13))
                .setGraph(Onnx.GraphProto.newBuilder()
                        .setName(MODEL_NAME)
                        .addInput(Onnx.ValueInfoProto.newBuilder().setName("input").setType(type))
                        .addOutput(Onnx.ValueInfoProto.newBuilder().setName("output").setType(type))
                        .addNode(Onnx.NodeProto.newBuilder().setOpType("Add")
                                .addInput("input").addInput("input").addOutput("output")))
                .build().toByteString();
    }
}
