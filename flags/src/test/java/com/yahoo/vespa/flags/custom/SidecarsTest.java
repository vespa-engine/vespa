package com.yahoo.vespa.flags.custom;

import com.yahoo.test.json.Jackson;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.exc.ValueInstantiationException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SidecarsTest {
    @Test
    void testSerializeWithAllValues() throws IOException {
        var sidecars = new Sidecars(List.of(
                new Sidecar(
                        1,
                        "triton",
                        "nvcr.io/nvidia/tritonserver:25.03-py3v",
                        new SidecarResources(8.0, 1.0, 4.0, "all"),
                        List.of("/models"),
                        Map.of("ORT_LOGGING_LEVEL_FATAL", "4"),
                        List.of(
                                "tritonserver",
                                "--log-verbose=1",
                                "--model-repository=/models",
                                "--model-control-mode=explicit",
                                "--backend-config=onnxruntime,enable-global-threadpool=1"
                        )
                ),
                new Sidecar(
                        2,
                        "triton",
                        "vllm/vllm-openai:latest",
                        new SidecarResources(4.0, 1.0, 2.0, "all"),
                        List.of("/root/.cache/huggingface"),
                        Map.of("HUGGING_FACE_HUB_TOKEN", "<secret>"),
                        List.of("vllm", "serve", "--model", "mistralai/Mistral-7B-v0.1")
                )
        ));

        var mapper = Jackson.mapper().writerWithDefaultPrettyPrinter();
        var serialized = mapper.writeValueAsString(sidecars);
        var expected = """
                {
                  "sidecars" : [ {
                    "id" : 1,
                    "name" : "triton",
                    "image" : "nvcr.io/nvidia/tritonserver:25.03-py3v",
                    "resources" : {
                      "maxCpu" : 8.0,
                      "minCpu" : 1.0,
                      "memoryGiB" : 4.0,
                      "gpu" : "all"
                    },
                    "volumeMounts" : [ "/models" ],
                    "envs" : {
                      "ORT_LOGGING_LEVEL_FATAL" : "4"
                    },
                    "command" : [ "tritonserver", "--log-verbose=1", "--model-repository=/models", "--model-control-mode=explicit", "--backend-config=onnxruntime,enable-global-threadpool=1" ]
                  }, {
                    "id" : 2,
                    "name" : "triton",
                    "image" : "vllm/vllm-openai:latest",
                    "resources" : {
                      "maxCpu" : 4.0,
                      "minCpu" : 1.0,
                      "memoryGiB" : 2.0,
                      "gpu" : "all"
                    },
                    "volumeMounts" : [ "/root/.cache/huggingface" ],
                    "envs" : {
                      "HUGGING_FACE_HUB_TOKEN" : "<secret>"
                    },
                    "command" : [ "vllm", "serve", "--model", "mistralai/Mistral-7B-v0.1" ]
                  } ]
                }""";

        assertEquals(expected, serialized);
    }

    @Test
    void testDeserializeCompleteJson() throws IOException {
        var json = """
                {
                  "sidecars" : [ {
                    "id" : 1,
                    "name" : "triton",
                    "image" : "nvcr.io/nvidia/tritonserver:25.03-py3v",
                    "resources" : {
                      "maxCpu" : 8.0,
                      "minCpu" : 1.0,
                      "memoryGiB" : 4.0,
                      "gpu" : "all"
                    },
                    "volumeMounts" : [ "/models" ],
                    "envs" : {
                      "ORT_LOGGING_LEVEL_FATAL" : "4"
                    },
                    "command" : [ "tritonserver", "--log-verbose=1" ]
                  } ]
                }""";

        var mapper = Jackson.mapper();
        var sidecars = mapper.readValue(json, Sidecars.class);
        
        var expectedSidecar = new Sidecar(
                1,
                "triton",
                "nvcr.io/nvidia/tritonserver:25.03-py3v",
                new SidecarResources(8.0, 1.0, 4.0, "all"),
                List.of("/models"),
                Map.of("ORT_LOGGING_LEVEL_FATAL", "4"),
                List.of("tritonserver", "--log-verbose=1")
        );
        var expectedSidecars = new Sidecars(List.of(expectedSidecar));
        
        assertEquals(expectedSidecars, sidecars);
    }

    @Test
    void testDeserializeWithNullValues() throws IOException {
        var json = """
                {
                  "sidecars" : [ {
                    "id" : 1,
                    "name" : "test",
                    "image" : "test:latest",
                    "resources" : null,
                    "volumeMounts" : null,
                    "envs" : null,
                    "command" : null
                  } ]
                }""";

        var mapper = Jackson.mapper();
        var sidecars = mapper.readValue(json, Sidecars.class);
        
        var expectedSidecar = new Sidecar(1, "test", "test:latest", SidecarResources.DEFAULT, List.of(), Map.of(), List.of());
        var expectedSidecars = new Sidecars(List.of(expectedSidecar));
        
        assertEquals(expectedSidecars, sidecars);
    }

    @Test
    void testDeserializeWithAbsentProperties() throws IOException {
        var json = """
                {
                  "sidecars" : [ {
                    "id" : 1,
                    "name" : "test",
                    "image" : "test:latest"
                  } ]
                }""";

        var mapper = Jackson.mapper();
        var sidecars = mapper.readValue(json, Sidecars.class);
        
        var expectedSidecar = new Sidecar(1, "test", "test:latest", SidecarResources.DEFAULT, List.of(), Map.of(), List.of());
        var expectedSidecars = new Sidecars(List.of(expectedSidecar));
        
        assertEquals(expectedSidecars, sidecars);
    }

    @Test
    void testDeserializeEmptySidecars() throws IOException {
        var json = """
                {
                  "sidecars" : []
                }""";

        var mapper = Jackson.mapper();
        var sidecars = mapper.readValue(json, Sidecars.class);
        
        assertEquals(0, sidecars.sidecars().size());
    }

    @Test
    void testDeserializeNullSidecars() throws IOException {
        var json = """
                {
                  "sidecars" : null
                }""";

        var mapper = Jackson.mapper();
        var sidecars = mapper.readValue(json, Sidecars.class);
        
        assertEquals(0, sidecars.sidecars().size());
    }

    @Test
    void testDeserializeAbsentSidecarsProperty() throws IOException {
        var json = "{}";

        var mapper = Jackson.mapper();
        var sidecars = mapper.readValue(json, Sidecars.class);
        
        assertEquals(0, sidecars.sidecars().size());
    }

    @Test
    void testSerializeWithNullValues() throws IOException {
        var sidecars = new Sidecars(List.of(
                new Sidecar(1, "test", "test:latest", null, null, null, null)
        ));

        var mapper = Jackson.mapper().writerWithDefaultPrettyPrinter();
        var serialized = mapper.writeValueAsString(sidecars);
        var expected = """
                {
                  "sidecars" : [ {
                    "id" : 1,
                    "name" : "test",
                    "image" : "test:latest",
                    "resources" : {
                      "maxCpu" : 0.0,
                      "minCpu" : 0.0,
                      "memoryGiB" : 0.0
                    },
                    "volumeMounts" : [ ],
                    "envs" : { },
                    "command" : [ ]
                  } ]
                }""";

        assertEquals(expected, serialized);
    }

    @Test
    void testSerializeEmptySidecars() throws IOException {
        var sidecars = new Sidecars(List.of());

        var mapper = Jackson.mapper().writerWithDefaultPrettyPrinter();
        var serialized = mapper.writeValueAsString(sidecars);
        var expected = """
                {
                  "sidecars" : [ ]
                }""";

        assertEquals(expected, serialized);
    }

    @Test
    void testSerializeNullSidecars() throws IOException {
        var sidecars = new Sidecars(null);

        var mapper = Jackson.mapper().writerWithDefaultPrettyPrinter();
        var serialized = mapper.writeValueAsString(sidecars);
        var expected = """
                {
                  "sidecars" : [ ]
                }""";

        assertEquals(expected, serialized);
    }

    @Test
    void testDeserializationWithMissingIdUsesDefault() throws IOException {
        var json = """
                {
                  "sidecars" : [ {
                    "name" : "test",
                    "image" : "test:latest"
                  } ]
                }""";

        var mapper = Jackson.mapper();
        var sidecars = mapper.readValue(json, Sidecars.class);
        
        var expectedSidecar = new Sidecar(0, "test", "test:latest", SidecarResources.DEFAULT, List.of(), Map.of(), List.of());
        var expectedSidecars = new Sidecars(List.of(expectedSidecar));
        
        assertEquals(expectedSidecars, sidecars);
    }

    @Test
    void testDeserializationFailsWhenRequiredNameMissing() {
        var json = """
                {
                  "sidecars" : [ {
                    "id" : 1,
                    "image" : "test:latest"
                  } ]
                }""";

        var mapper = Jackson.mapper();
        assertThrows(ValueInstantiationException.class, () -> {
            mapper.readValue(json, Sidecars.class);
        });
    }

    @Test
    void testDeserializationFailsWhenRequiredImageMissing() {
        var json = """
                {
                  "sidecars" : [ {
                    "id" : 1,
                    "name" : "test"
                  } ]
                }""";

        var mapper = Jackson.mapper();
        assertThrows(ValueInstantiationException.class, () -> {
            mapper.readValue(json, Sidecars.class);
        });
    }

    @Test
    void testDeserializationFailsWhenNameIsNull() {
        var json = """
                {
                  "sidecars" : [ {
                    "id" : 1,
                    "name" : null,
                    "image" : "test:latest"
                  } ]
                }""";

        var mapper = Jackson.mapper();
        assertThrows(ValueInstantiationException.class, () -> {
            mapper.readValue(json, Sidecars.class);
        });
    }

    @Test
    void testDeserializationFailsWhenImageIsNull() {
        var json = """
                {
                  "sidecars" : [ {
                    "id" : 1,
                    "name" : "test",
                    "image" : null
                  } ]
                }""";

        var mapper = Jackson.mapper();
        assertThrows(ValueInstantiationException.class, () -> {
            mapper.readValue(json, Sidecars.class);
        });
    }

    @Test
    void testValidationFailsWhenSidecarIdsNotUnique() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Sidecars(List.of(
                    new Sidecar(1, "test1", "test1:latest", null, null, null, null),
                    new Sidecar(1, "test2", "test2:latest", null, null, null, null)
            ));
        });
    }

    @Test
    void testValidationFailsWhenSidecarIdOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Sidecar(-1, "test", "test:latest", null, null, null, null);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new Sidecar(100, "test", "test:latest", null, null, null, null);
        });
    }

    @Test
    void testValidationFailsWhenSidecarNameIsBlank() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Sidecar(1, "", "test:latest", null, null, null, null);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new Sidecar(1, "   ", "test:latest", null, null, null, null);
        });
    }

    @Test
    void testValidationFailsWhenSidecarImageIsBlank() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Sidecar(1, "test", "", null, null, null, null);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new Sidecar(1, "test", "   ", null, null, null, null);
        });
    }

    @Test
    void testGpuValidationWithValidValues() {
        // Test null GPU (no GPU)
        var resources1 = new SidecarResources(1.0, 0.5, 2.0, null);
        assertEquals(null, resources1.gpu());

        // Test "all" GPU
        var resources2 = new SidecarResources(1.0, 0.5, 2.0, "all");
        assertEquals("all", resources2.gpu());

        // Test single device index
        var resources3 = new SidecarResources(1.0, 0.5, 2.0, "0");
        assertEquals("0", resources3.gpu());

        // Test multiple device indexes
        var resources4 = new SidecarResources(1.0, 0.5, 2.0, "0,1,2");
        assertEquals("0,1,2", resources4.gpu());

        // Test with spaces around indexes
        var resources5 = new SidecarResources(1.0, 0.5, 2.0, " 0 , 1 , 2 ");
        assertEquals(" 0 , 1 , 2 ", resources5.gpu());
    }

    @Test
    void testGpuValidationFailsWithNegativeIndexes() {
        assertThrows(IllegalArgumentException.class, () -> {
            new SidecarResources(1.0, 0.5, 2.0, "-1");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new SidecarResources(1.0, 0.5, 2.0, "0,-1");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new SidecarResources(1.0, 0.5, 2.0, "-1,0,1");
        });
    }

    @Test
    void testGpuValidationFailsWithDuplicateIndexes() {
        assertThrows(IllegalArgumentException.class, () -> {
            new SidecarResources(1.0, 0.5, 2.0, "0,0");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new SidecarResources(1.0, 0.5, 2.0, "0,1,0");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new SidecarResources(1.0, 0.5, 2.0, "1,2,3,1");
        });
    }

    @Test
    void testGpuValidationFailsWithInvalidFormats() {
        assertThrows(IllegalArgumentException.class, () -> {
            new SidecarResources(1.0, 0.5, 2.0, "invalid");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new SidecarResources(1.0, 0.5, 2.0, "0,invalid");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new SidecarResources(1.0, 0.5, 2.0, "0.5");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new SidecarResources(1.0, 0.5, 2.0, "");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new SidecarResources(1.0, 0.5, 2.0, ",0");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new SidecarResources(1.0, 0.5, 2.0, "0,");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            new SidecarResources(1.0, 0.5, 2.0, "1,2,");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            new SidecarResources(1.0, 0.5, 2.0, "0,1,2,");
        });
    }
}
