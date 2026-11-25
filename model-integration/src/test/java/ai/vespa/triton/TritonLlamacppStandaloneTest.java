// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.llm.clients.TritonConfig;
import ai.vespa.modelintegration.llamacpp.LlamacppOptions;
import com.yahoo.language.process.InvalidInputException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests TritonLlamacppRuntime against a standalone llama-server, which it only sends inference requests to.
 * Runs the llama-server of Vespa's Triton image with llama.cpp (Triton 26.11 or later), e.g.
 * -DVESPA_USE_TRITON=true -DVESPA_TRITON_IMAGE=localhost/tritonserver:<tag>-cpu-onnx-llamacpp
 *
 * @author ingridhabrekke
 */
@EnabledIfSystemProperty(named = "VESPA_USE_TRITON", matches = "true")
@ExtendWith(ContainerEnvironmentAvailableCondition.class)
class TritonLlamacppStandaloneTest {

    private static final Path GGUF_MODEL_PATH = Path.of("src/test/models/llm/tinyllm.gguf");
    private static final int PORT = 8080;

    private static GenericContainer<?> llamaServer;

    @BeforeAll
    static void startLlamaServer() {
        assumeTrue(TritonServerContainer.isLlamacppImageConfigured(), "Needs a Vespa Triton image with llama.cpp");
        llamaServer = new GenericContainer<>(DockerImageName.parse(System.getProperty("VESPA_TRITON_IMAGE")))
                .withFileSystemBind(GGUF_MODEL_PATH.toAbsolutePath().toString(), "/model.gguf", BindMode.READ_ONLY)
                .withCommand("/opt/llamacpp/llama-server", "--model", "/model.gguf", "--embeddings",
                             "--pooling", "mean", "--ctx-size", "512", "--host", "0.0.0.0", "--port", Integer.toString(PORT))
                .withExposedPorts(PORT)
                .waitingFor(Wait.forHttp("/health").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(2)));
        llamaServer.start();
    }

    @AfterAll
    static void stopLlamaServer() {
        if (llamaServer != null) llamaServer.stop();
    }

    @Test
    void embeds_and_tokenizes_with_a_standalone_llama_server() {
        var runtime = new TritonLlamacppRuntime(config());
        try (var evaluator = runtime.evaluatorOf(GGUF_MODEL_PATH.toString(), options())) {
            var embedding = evaluator.embed("This is a test", Optional.empty());
            assertEquals(64, embedding.length);

            var tokens = evaluator.tokenize("This is a test", Optional.empty());
            assertTrue(tokens.length > 0);
            assertEquals("This is a test", evaluator.detokenize(tokens, Optional.empty()).strip());

            assertThrows(InvalidInputException.class, () -> evaluator.embed("This is a test. ".repeat(1000), Optional.empty()));
        } finally {
            runtime.deconstruct();
        }
    }

    // The server's own flags decide how it runs the model; these options only give the model's name.
    private static LlamacppOptions options() {
        return new LlamacppOptions(1, 0, -1, -1, 0, Optional.of(LlamacppOptions.Pooling.MEAN), -1, 0, 0, false);
    }

    // Nothing serves the gRPC target: the runtime must not manage models in this mode.
    private static TritonConfig config() {
        return new TritonConfig.Builder()
                .target("localhost:1")
                .llamacppEndpoint("http://" + llamaServer.getHost() + ":" + llamaServer.getMappedPort(PORT))
                .modelControlMode(TritonConfig.ModelControlMode.NONE)
                .build();
    }

}
