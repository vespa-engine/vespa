package com.yahoo.vespa.flags.custom;

import com.yahoo.test.json.Jackson;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SidecarsTest {
    @Test
    void testWithAllValues() throws IOException {
        verifySerialization(new Sidecars(List.of(
                new Sidecar(
                        "triton",
                        "nvcr.io/nvidia/tritonserver:25.03-py3v",
                        new SidecarQuota(1.0, 8.0, "all"),
                        List.of("/models"),
                        Map.of("ORT_LOGGING_LEVEL_FATAL", "4"),
                        List.of(
                                "tritonserver",
                                "--log-verbose=1",
                                "--model-repository=/models",
                                "--model-control-mode=explicit",
                                "--backend-config=onnxruntime,enable-global-threadpool=1")),
                new Sidecar(
                        "triton",
                        "vllm/vllm-openai:latest",
                        new SidecarQuota(1.0, 8.0, "all"),
                        List.of("/root/.cache/huggingface"),
                        Map.of("HUGGING_FACE_HUB_TOKEN", "<secret>"),
                        List.of("vllm", "serve", "--model", "mistralai/Mistral-7B-v0.1")))));
    }

    @Test
    void testWithNulls() throws IOException {
        verifySerialization(new Sidecars(List.of(new Sidecar(
                "triton", "nginx:alpine", new SidecarQuota(null, 8.0, null), List.of(), Map.of(), List.of()))));
    }

    @Test
    void testDisabled() throws IOException {
        verifySerialization(Sidecars.createDisabled());
    }

    private void verifySerialization(Sidecars sidecars) throws IOException {
        var mapper = Jackson.mapper();
        String json = mapper.writeValueAsString(sidecars);
        var deserialized = mapper.readValue(json, Sidecars.class);
        assertEquals(sidecars, deserialized);
    }
}
