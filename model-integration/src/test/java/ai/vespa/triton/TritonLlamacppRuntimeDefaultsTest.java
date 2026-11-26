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

    // llama-server batches the requests of its slots continuously, so Triton must not batch them as well.
    @Test
    void triton_does_not_batch_and_llama_server_batches_continuously() throws IOException {
        for (boolean continuousBatching : List.of(false, true)) {
            var options = new LlamacppOptions(4, 8192, 2048, 1024, 0, Optional.empty(), -1, 0, 0, continuousBatching);
            var config = parse(TritonLlamacppRuntime.createModelConfig("model_abc", options));

            assertEquals(0, config.getMaxBatchSize());
            assertFalse(config.hasDynamicBatching());
            assertFalse(config.hasSequenceBatching());
            assertFalse(parameters(config).containsKey("llamacpp.no-cont-batching"));
        }
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

    @Test
    void model_name_keeps_the_start_of_a_long_file_name(@TempDir Path directory) throws IOException {
        var prefix = "an-embedding-model-with-a-long-file-name-";
        var model = Files.writeString(directory.resolve(prefix + "q8_0.gguf"), "model contents");
        var otherModel = Files.writeString(directory.resolve(prefix + "q4_k_m.gguf"), "other contents");

        var name = TritonLlamacppRuntime.generateModelName(model.toString(), DEFAULT_OPTIONS);
        var otherName = TritonLlamacppRuntime.generateModelName(otherModel.toString(), DEFAULT_OPTIONS);
        assertTrue(name.startsWith(prefix.substring(0, TritonLlamacppRuntime.MAX_BASE_NAME_LENGTH) + "_"), name);
        var hashSuffixLength = "_".length() + 16; // An underscore and up to 16 hex digits
        assertTrue(name.length() <= TritonLlamacppRuntime.MAX_BASE_NAME_LENGTH + hashSuffixLength, name);
        assertNotEquals(name, otherName);
    }

    @Test
    void image_token_options_become_llama_server_flags() throws IOException {
        var options = new LlamacppOptions(1, 0, -1, -1, 0, Optional.empty(), -1, 0, 0, false, 250, 280);
        var parameters = parameters(parse(TritonLlamacppRuntime.createModelConfig("model_abc", options)));

        assertEquals("250", parameters.get("llamacpp.image-min-tokens"));
        assertEquals("280", parameters.get("llamacpp.image-max-tokens"));
    }

    @Test
    void model_name_includes_the_projector_if_any(@TempDir Path directory) throws IOException {
        var model = Files.writeString(directory.resolve("embedder.gguf"), "model contents");
        var mmproj = Files.writeString(directory.resolve("mmproj.gguf"), "projector contents");
        var otherMmproj = Files.writeString(directory.resolve("other-mmproj.gguf"), "other projector contents");

        var name = TritonLlamacppRuntime.generateModelName(model.toString(), DEFAULT_OPTIONS);
        assertEquals(name, TritonLlamacppRuntime.generateModelName(model.toString(), Optional.empty(), DEFAULT_OPTIONS));
        var withMmproj = TritonLlamacppRuntime.generateModelName(model.toString(), Optional.of(mmproj.toString()), DEFAULT_OPTIONS);
        assertNotEquals(name, withMmproj);
        assertNotEquals(withMmproj, TritonLlamacppRuntime.generateModelName(model.toString(), Optional.of(otherMmproj.toString()), DEFAULT_OPTIONS));
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
