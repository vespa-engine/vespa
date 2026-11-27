// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.llamacpp;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the llama.cpp arguments that EmbeddedLlamacppRuntime gives its models, without loading a model.
 *
 * @author glebashnik
 */
class EmbeddedLlamacppRuntimeTest {

    private static final String MODEL_PATH = "model.gguf";

    @Test
    void default_gpu_layers_leave_the_gpu_offload_to_llama_cpp() {
        var arguments = argumentsOf(-1);
        assertFalse(arguments.contains("--gpu-layers"), arguments.toString());
    }

    @Test
    void zero_gpu_layers_put_no_layers_on_a_gpu() {
        assertEquals("0", gpuLayersOf(argumentsOf(0)));
    }

    @Test
    void gpu_layers_cap_the_layers_on_a_gpu() {
        assertEquals("99", gpuLayersOf(argumentsOf(99)));
    }

    private static List<String> argumentsOf(int gpuLayers) {
        var options = new LlamacppOptions(1, 0, -1, -1, gpuLayers, Optional.empty(), -1, 0, 0, false);
        return List.of(EmbeddedLlamacppRuntime.modelParameters(MODEL_PATH, options).toArray());
    }

    private static String gpuLayersOf(List<String> arguments) {
        int index = arguments.indexOf("--gpu-layers");
        assertTrue(index >= 0, "No --gpu-layers in " + arguments);
        return arguments.get(index + 1);
    }

}
