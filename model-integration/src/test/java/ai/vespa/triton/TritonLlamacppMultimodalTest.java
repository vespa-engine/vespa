// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.llm.clients.TritonConfig;
import ai.vespa.modelintegration.llamacpp.LlamacppEvaluator.Part;
import ai.vespa.modelintegration.llamacpp.LlamacppOptions;
import com.yahoo.language.process.Embedder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests image embeddings of a GGUF model with a multimodal projector in Triton, given with
 * -DVESPA_USE_TRITON=true -DVESPA_TRITON_IMAGE=<image with llamacpp in its name>
 * -DVESPA_GGUF_MULTIMODAL_MODEL=<model .gguf file> -DVESPA_GGUF_MULTIMODAL_MMPROJ=<projector .gguf file>
 * An image takes minutes on a CPU.
 *
 * @author glebashnik
 */
@EnabledIfSystemProperty(named = "VESPA_USE_TRITON", matches = "true")
@ExtendWith(ContainerEnvironmentAvailableCondition.class)
class TritonLlamacppMultimodalTest {

    private static final String MODEL_PROPERTY = "VESPA_GGUF_MULTIMODAL_MODEL";
    private static final String MMPROJ_PROPERTY = "VESPA_GGUF_MULTIMODAL_MMPROJ";

    // A slot must hold a whole input, and llama-server caps the tokens of an image at half the micro-batch.
    private static final LlamacppOptions OPTIONS =
            new LlamacppOptions(1, 2048, 2048, 2048, 0, Optional.empty(), -1, 0, 0, false, 0, 0);

    private static TritonServerContainer tritonContainer;

    @BeforeAll
    static void setupTritonContainer() throws IOException {
        assumeTrue(TritonServerContainer.isLlamacppImageConfigured(), "Needs a Triton image with llama.cpp");
        assumeTrue(System.getProperty(MODEL_PROPERTY) != null && System.getProperty(MMPROJ_PROPERTY) != null,
                   "Needs a multimodal GGUF model and its projector");
        tritonContainer = TritonServerContainer.withLlamacpp();
        tritonContainer.start();
    }

    @AfterAll
    static void stopTritonContainer() {
        if (tritonContainer != null) tritonContainer.stop();
    }

    @Test
    void embeds_an_image_alone_and_with_a_text() throws IOException {
        var modelPath = System.getProperty(MODEL_PROPERTY);
        var mmprojPath = Optional.of(System.getProperty(MMPROJ_PROPERTY));
        var runtime = new TritonLlamacppRuntime(config());
        try (var evaluator = runtime.evaluatorOf(modelPath, mmprojPath, OPTIONS)) {
            var modelName = TritonLlamacppRuntime.generateModelName(modelPath, mmprojPath, OPTIONS);
            assertTrue(Files.isRegularFile(tritonContainer.getModelRepositoryPath().resolve(modelName).resolve("1/mmproj.gguf")));
            assertTrue(evaluator.acceptsImages());

            var image = new Embedder.Image(jpeg(Color.RED));
            var imageEmbedding = evaluator.embed(List.of(new Part.Image(image)), Optional.empty());
            var textEmbedding = evaluator.embed("A red square", Optional.empty());
            assertEquals(textEmbedding.length, imageEmbedding.length);
            assertEquals(1.0, norm(imageEmbedding), 1e-3);

            var textAndImageEmbedding = evaluator.embed(List.of(new Part.Text("A red square "), new Part.Image(image)),
                                                        Optional.empty());
            assertTrue(cosine(imageEmbedding, textAndImageEmbedding) < 0.9999, "The text changes the embedding");
            var otherImageEmbedding = evaluator.embed(List.of(new Part.Image(new Embedder.Image(jpeg(Color.BLUE)))),
                                                      Optional.empty());
            assertTrue(cosine(imageEmbedding, otherImageEmbedding) < 0.9999, "Another image gives another embedding");
        } finally {
            runtime.deconstruct();
        }
    }

    private static byte[] jpeg(Color color) throws IOException {
        var image = new BufferedImage(224, 224, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 224, 224);
        graphics.setColor(color);
        graphics.fillRect(48, 48, 128, 128);
        graphics.dispose();
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", bytes);
        return bytes.toByteArray();
    }

    private static double norm(float[] vector) {
        double sum = 0;
        for (float value : vector) sum += value * value;
        return Math.sqrt(sum);
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) dot += a[i] * b[i];
        return dot / (norm(a) * norm(b));
    }

    // An image takes minutes on a CPU, longer than the default timeout.
    private static TritonConfig config() {
        return new TritonConfig.Builder()
                .target(tritonContainer.getGrpcEndpoint())
                .llamacppEndpoint(tritonContainer.getLlamacppEndpoint())
                .modelControlMode(TritonConfig.ModelControlMode.EXPLICIT)
                .modelRepositoryPath(tritonContainer.getModelRepositoryPath().toString())
                .timeout(Duration.ofMinutes(30).toMillis())
                .build();
    }

}
