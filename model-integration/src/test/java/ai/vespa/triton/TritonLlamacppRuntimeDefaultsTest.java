// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.modelintegration.llamacpp.LlamacppOptions;
import com.google.protobuf.TextFormat;
import inference.ModelConfigOuterClass.ModelConfig;
import inference.ModelConfigOuterClass.ModelInstanceGroup.Kind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the model configs and names that TritonLlamacppRuntime generates, without Triton.
 *
 * @author ingridhabrekke
 */
class TritonLlamacppRuntimeDefaultsTest {

    // The options of a GGUF embedder with the default config.
    private static final LlamacppOptions DEFAULT_OPTIONS =
            new LlamacppOptions(1, 0, -1, -1, 0, Optional.empty(), -1, 0, 0, false);

    @Test
    void default_options_give_an_embedding_model_with_one_slot_on_cpu() throws IOException {
        var config = parse(TritonLlamacppRuntime.createModelConfig("model_abc", DEFAULT_OPTIONS));

        assertEquals("model_abc", config.getName());
        assertEquals("llamacpp", config.getBackend());
        assertEquals(1, config.getInstanceGroupCount());
        assertEquals(1, config.getInstanceGroup(0).getCount());
        assertEquals(Kind.KIND_CPU, config.getInstanceGroup(0).getKind());
        assertEquals(0, config.getInstanceGroup(0).getGpusCount());
        assertEquals(Map.of("llamacpp.embeddings", "", "llamacpp.n-gpu-layers", "0"), parameters(config));
    }

    @Test
    void gpu_layers_give_instances_on_a_gpu_if_there_is_one() throws IOException {
        var options = new LlamacppOptions(2, 0, -1, -1, 99, Optional.empty(), -1, 0, 0, false);
        var group = parse(TritonLlamacppRuntime.createModelConfig("model_abc", options)).getInstanceGroup(0);

        assertEquals(Kind.KIND_AUTO, group.getKind());
        assertEquals(List.of(), group.getGpusList()); // Triton refuses GPUs on a host without them
        assertEquals(2, group.getCount());
    }

    @Test
    void slots_must_hold_an_input_and_divide_the_micro_batch() {
        // 4096 / 8 = 512 per slot holds llama.cpp's default micro-batch of 512, which 8 slots divide
        TritonLlamacppRuntime.validateSlots(new LlamacppOptions(8, 4096, -1, -1, 0, Optional.empty(), -1, 0, 0, false));

        var tooSmallSlots = assertThrows(IllegalArgumentException.class, () -> TritonLlamacppRuntime.validateSlots(
                new LlamacppOptions(4, 2048, -1, 1024, 0, Optional.empty(), -1, 0, 0, false)));
        assertEquals("contextSize 2048 gives 512 per parallel sequence (parallel 4), which is below " +
                     "physicalMaxBatchSize 1024: each sequence must hold a whole input", tooSmallSlots.getMessage());

        var notDividing = assertThrows(IllegalArgumentException.class, () -> TritonLlamacppRuntime.validateSlots(
                new LlamacppOptions(3, 0, -1, -1, 0, Optional.empty(), -1, 0, 0, false)));
        assertEquals("parallel 3 must divide llama.cpp's default physicalMaxBatchSize 512 for llama.cpp in Triton",
                     notDividing.getMessage());
    }

    @Test
    void options_map_to_llama_server_flags_with_one_slot_per_instance() throws IOException {
        var options = new LlamacppOptions(4, 8192, 2048, 1024, 99, Optional.of(LlamacppOptions.Pooling.LAST), 42, 8, 6, true);
        var config = parse(TritonLlamacppRuntime.createModelConfig("model_abc", options));

        assertEquals(4, config.getInstanceGroup(0).getCount());
        assertEquals(Map.of("llamacpp.embeddings", "",
                            "llamacpp.pooling", "last",
                            "llamacpp.ctx-size", "2048", // The context per slot, 8192 / 4
                            "llamacpp.batch-size", "2048",
                            "llamacpp.ubatch-size", "1024",
                            "llamacpp.n-gpu-layers", "99",
                            "llamacpp.seed", "42",
                            "llamacpp.threads", "8",
                            "llamacpp.threads-batch", "6"),
                     parameters(config));
    }

    @Test
    void model_name_is_stable_and_differs_by_model_and_options(@TempDir Path directory) throws IOException {
        var model = Files.writeString(directory.resolve("embedder.gguf"), "model contents");
        var otherModel = Files.writeString(directory.resolve("other.gguf"), "other contents");
        var otherOptions = new LlamacppOptions(2, 0, -1, -1, 0, Optional.empty(), -1, 0, 0, false);

        var name = TritonLlamacppRuntime.generateModelName(model.toString(), DEFAULT_OPTIONS);
        assertTrue(name.startsWith("embedder_"), name);
        assertEquals(name, TritonLlamacppRuntime.generateModelName(model.toString(), DEFAULT_OPTIONS));
        assertNotEquals(name, TritonLlamacppRuntime.generateModelName(model.toString(), otherOptions));
        assertNotEquals(hashOf(name), hashOf(TritonLlamacppRuntime.generateModelName(otherModel.toString(), DEFAULT_OPTIONS)));
    }

    private static String hashOf(String modelName) {
        return modelName.substring(modelName.lastIndexOf('_') + 1);
    }

    @Test
    void only_models_with_llamacpp_configs_are_llamacpp_models(@TempDir Path repository) throws IOException {
        var llamacppModel = writeConfig(repository, "llamacpp", TritonLlamacppRuntime.createModelConfig("llamacpp", DEFAULT_OPTIONS));
        var onnxModel = writeConfig(repository, "onnx", "name: \"onnx\"\nplatform: \"onnxruntime_onnx\"\n");
        var brokenModel = writeConfig(repository, "broken", "not a model config {");
        var modelWithoutConfig = Files.createDirectories(repository.resolve("without_config/1"));

        assertTrue(TritonLlamacppRuntime.isLlamacppModel(llamacppModel));
        assertFalse(TritonLlamacppRuntime.isLlamacppModel(onnxModel));
        assertFalse(TritonLlamacppRuntime.isLlamacppModel(brokenModel));
        assertFalse(TritonLlamacppRuntime.isLlamacppModel(modelWithoutConfig.getParent()));
        assertFalse(TritonLlamacppRuntime.isLlamacppModel(repository.resolve("missing")));
    }

    @Test
    void no_pooling_is_rejected_since_triton_returns_only_pooled_embeddings() {
        var runtime = new TritonLlamacppRuntime(new ai.vespa.llm.clients.TritonConfig.Builder()
                                                     .modelControlMode(ai.vespa.llm.clients.TritonConfig.ModelControlMode.NONE).build());
        var options = new LlamacppOptions(1, 0, -1, -1, 0, Optional.of(LlamacppOptions.Pooling.NONE), -1, 0, 0, false);
        try {
            var e = assertThrows(IllegalArgumentException.class, () -> runtime.evaluatorOf("model.gguf", options));
            assertTrue(e.getMessage().startsWith("Pooling type NONE is not supported"), e.getMessage());
        } finally {
            runtime.deconstruct();
        }
    }

    private static Path writeConfig(Path repository, String modelName, String config) throws IOException {
        var modelDir = Files.createDirectories(repository.resolve(modelName));
        Files.writeString(modelDir.resolve("config.pbtxt"), config);
        return modelDir;
    }

    private static ModelConfig parse(String config) throws IOException {
        return TextFormat.parse(config, ModelConfig.class);
    }

    private static Map<String, String> parameters(ModelConfig config) {
        return config.getParametersMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getStringValue()));
    }

}
