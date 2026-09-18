// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.llm.clients.TritonConfig;
import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
import com.google.protobuf.ByteString;
import com.google.protobuf.TextFormat;
import inference.ModelConfigOuterClass.ModelConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static ai.vespa.triton.TritonGpuProbeTest.config;
import static inference.ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_CPU;
import static inference.ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_GPU;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class TritonOnnxRuntimeGpuProbeTest {
    @TempDir Path directory;
    private final TritonOnnxClient client = mock(TritonOnnxClient.class);
    private final OnnxEvaluatorOptions options = new OnnxEvaluatorOptions.Builder(8).build();

    private TritonOnnxRuntime runtime(TritonConfig.ModelControlMode.Enum mode) {
        return new TritonOnnxRuntime(new TritonConfig.Builder()
                .modelControlMode(mode)
                .modelRepositoryPath(directory.resolve("repository").toString())
                .shareOnnxSessionBetweenInstances(true).build(), client);
    }

    @Test
    void detected_gpu_is_used_for_both_name_and_config() throws IOException {
        when(client.getModelConfig(anyString())).thenReturn(config(KIND_GPU, 2, 4));
        var runtime = runtime(TritonConfig.ModelControlMode.EXPLICIT);
        try {
            var model = Files.writeString(directory.resolve("model.onnx"), "test model").toString();
            var resolved = TritonOnnxRuntime.resolveOptions(options, true, Set.of(2, 4));
            var name = TritonOnnxRuntime.generateModelName(model, resolved, true);
            try (var first = runtime.evaluatorOf(model, options); var second = runtime.evaluatorOf(model, options)) {
                var generated = readConfig(name);
                assertEquals(name, generated.getName());
                assertEquals(2, generated.getInstanceGroup(0).getCount());
                assertEquals(KIND_GPU, generated.getInstanceGroup(0).getKind());
                assertEquals(List.of(2), generated.getInstanceGroup(0).getGpusList());
                verify(client, times(1)).loadModel(anyString(), anyString(), any(ByteString.class));
                var order = inOrder(client);
                order.verify(client).unloadAllModels();
                order.verify(client).loadModel(anyString(), anyString(), any(ByteString.class));
                order.verify(client).getModelConfig(anyString());
                order.verify(client).unloadModel(anyString());
                order.verify(client).unloadUntilModelNotReady(anyString());
                order.verify(client).isModelReady(name);
            }
        } finally {
            runtime.deconstruct();
        }
    }

    @Test
    void cpu_result_uses_cpu_defaults() throws IOException {
        when(client.getModelConfig(anyString())).thenReturn(config(KIND_CPU));
        var runtime = runtime(TritonConfig.ModelControlMode.EXPLICIT);
        try {
            var model = Files.writeString(directory.resolve("model.onnx"), "test model").toString();
            var name = TritonOnnxRuntime.generateModelName(model, options.withNumModelInstances(8), true);
            try (var evaluator = runtime.evaluatorOf(model, options)) {
                var group = readConfig(name).getInstanceGroup(0);
                assertEquals(8, group.getCount());
                assertEquals(KIND_CPU, group.getKind());
                assertTrue(group.getGpusList().isEmpty());
            }
        } finally {
            runtime.deconstruct();
        }
    }

    @Test
    void full_config_override_keeps_its_device_placement_and_does_not_probe() throws IOException {
        var runtime = runtime(TritonConfig.ModelControlMode.EXPLICIT);
        try {
            var model = Files.writeString(directory.resolve("model.onnx"), "test model").toString();
            for (var group : List.of("kind: KIND_CPU count: 3", "kind: KIND_GPU count: 3 gpus: [7]")) {
                var config = Files.writeString(directory.resolve("override.pbtxt"),
                        "platform: \"onnxruntime_onnx\"\ninstance_group { " + group + " }");
                var overrideOptions = new OnnxEvaluatorOptions.Builder(options).setGpuDevice(2)
                        .setModelConfigOverride(Optional.of(config)).build();
                var name = TritonOnnxRuntime.generateModelName(model, overrideOptions, true);
                try (var evaluator = runtime.evaluatorOf(model, overrideOptions)) {
                    assertEquals(TextFormat.parse(Files.readString(config), ModelConfig.class).getInstanceGroup(0),
                                 readConfig(name).getInstanceGroup(0));
                    verify(client, never()).loadModel(anyString(), anyString(), any(ByteString.class));
                    verify(client, never()).getModelConfig(anyString());
                }
            }
        } finally {
            runtime.deconstruct();
        }
    }

    @Test
    void unavailable_device_choice_fails_before_installing_a_model() throws IOException {
        when(client.getModelConfig(anyString())).thenReturn(config(KIND_GPU, 0));
        var runtime = runtime(TritonConfig.ModelControlMode.EXPLICIT);
        try {
            var model = Files.writeString(directory.resolve("model.onnx"), "test model").toString();
            var gpuOptions = new OnnxEvaluatorOptions.Builder(options).setGpuDevice(2).build();
            var failure = assertThrows(TritonOnnxClient.TritonException.class,
                                       () -> runtime.evaluatorOf(model, gpuOptions));
            assertTrue(failure.getMessage().contains("Requested GPU 2"));
            assertFalse(Files.exists(directory.resolve("repository")));
            verify(client, never()).loadUntilModelReady(anyString());
        } finally {
            runtime.deconstruct();
        }
    }

    @Test
    void cpu_probe_rejects_required_gpu_but_allows_an_optional_request() throws IOException {
        when(client.getModelConfig(anyString())).thenReturn(config(KIND_CPU));
        var runtime = runtime(TritonConfig.ModelControlMode.EXPLICIT);
        try {
            var model = Files.writeString(directory.resolve("model.onnx"), "test model").toString();
            var required = new OnnxEvaluatorOptions.Builder(options).setGpuDevice(2, true).build();
            var failure = assertThrows(TritonOnnxClient.TritonException.class,
                                       () -> runtime.evaluatorOf(model, required));
            assertEquals("Requested GPU 2 is required, but Triton reported no GPU devices", failure.getMessage());
            assertFalse(Files.exists(directory.resolve("repository")));
            verify(client, never()).loadUntilModelReady(anyString());
            verify(client, never()).getModelMetadata(anyString());

            var optional = new OnnxEvaluatorOptions.Builder(required).setGpuDevice(2, false).build();
            var name = TritonOnnxRuntime.generateModelName(model, options.withNumModelInstances(8), true);
            try (var evaluator = runtime.evaluatorOf(model, optional)) {
                var group = readConfig(name).getInstanceGroup(0);
                assertEquals(KIND_CPU, group.getKind());
                assertTrue(group.getGpusList().isEmpty());
                assertEquals(8, group.getCount());
                verify(client, times(1)).loadModel(anyString(), anyString(), any(ByteString.class));
                verify(client).loadUntilModelReady(name);
            }
        } finally {
            runtime.deconstruct();
        }
    }

    @Test
    void non_explicit_modes_use_existing_models_without_probing_or_generating_configs() throws IOException {
        var model = Files.writeString(directory.resolve("model.onnx"), "test model").toString();
        // This would fail if the runtime tried to parse an override and generate a model config.
        var override = Files.writeString(directory.resolve("override.pbtxt"), "not a valid model config");
        var gpuOptions = new OnnxEvaluatorOptions.Builder(options).setGpuDevice(2, true).build();
        var overrideOptions = new OnnxEvaluatorOptions.Builder(options)
                .setModelConfigOverride(Optional.of(override)).build();
        for (var mode : List.of(TritonConfig.ModelControlMode.NONE, TritonConfig.ModelControlMode.POLL)) {
            for (var modelOptions : List.of(options, gpuOptions, overrideOptions)) {
                var runtime = runtime(mode);
                var name = TritonOnnxRuntime.generateModelName(model, modelOptions, true);
                try {
                    runtime.evaluatorOf(model, modelOptions).close();
                    verify(client).getModelMetadata(name);
                    verifyNoMoreInteractions(client); // No probe, readiness, load or unload requests
                } finally {
                    runtime.deconstruct();
                }
                verify(client).close();
                verifyNoMoreInteractions(client);
                assertFalse(Files.exists(directory.resolve("repository")));
                clearInvocations(client);
            }
        }
    }

    @Test
    void non_explicit_modes_preserve_existing_repository_files() throws IOException {
        var model = Files.writeString(directory.resolve("model.onnx"), "test model").toString();
        var repository = Files.createDirectories(directory.resolve("repository"));
        var config = Files.writeString(repository.resolve("config.pbtxt"), "externally managed config");
        for (var mode : List.of(TritonConfig.ModelControlMode.NONE, TritonConfig.ModelControlMode.POLL)) {
            var runtime = runtime(mode);
            try {
                runtime.evaluatorOf(model, options).close();
            } finally {
                runtime.deconstruct();
            }
            assertEquals("externally managed config", Files.readString(config));
            try (var files = Files.list(repository)) {
                assertEquals(List.of(config), files.toList());
            }
        }
    }

    @Test
    void probe_failure_prevents_loading_the_real_model_and_can_be_retried() throws IOException {
        when(client.getModelConfig(anyString()))
                .thenThrow(new TritonOnnxClient.TritonException("probe unavailable"))
                .thenReturn(config(KIND_GPU, 2));
        var runtime = runtime(TritonConfig.ModelControlMode.EXPLICIT);
        try {
            var model = Files.writeString(directory.resolve("model.onnx"), "test model").toString();
            assertThrows(TritonOnnxClient.TritonException.class, () -> runtime.evaluatorOf(model, options));
            assertFalse(Files.exists(directory.resolve("repository")));
            verify(client, never()).loadUntilModelReady(anyString());
            try (var evaluator = runtime.evaluatorOf(model, options)) {
                verify(client, times(2)).loadModel(anyString(), anyString(), any(ByteString.class));
                verify(client).loadUntilModelReady(anyString());
            }
        } finally {
            runtime.deconstruct();
        }
    }

    private ModelConfig readConfig(String name) throws IOException {
        return TextFormat.parse(Files.readString(directory.resolve("repository").resolve(name).resolve("config.pbtxt")),
                                ModelConfig.class);
    }
}
