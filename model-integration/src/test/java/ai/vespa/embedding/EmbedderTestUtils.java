// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.secret.Secrets;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.text.Text;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

/**
 * Shared test utilities for embedder tests.
 *
 * @author bjorncs
 */
class EmbedderTestUtils {

    private EmbedderTestUtils() {}

    static Secrets createMockSecrets() {
        return key -> {
            if ("test_key".equals(key)) return () -> "test-api-key-12345";
            return null;
        };
    }

    static double cosineSimilarity(Tensor a, Tensor b) {
        double dotProduct = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.size(); i++) {
            double valA = a.get(TensorAddress.of(i));
            double valB = b.get(TensorAddress.of(i));
            dotProduct += valA * valB;
            normA += valA * valA;
            normB += valB * valB;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    static void assertNonZeroTensor(Tensor tensor) {
        for (int i = 0; i < tensor.size(); i++) {
            if (Math.abs(tensor.get(TensorAddress.of(i))) > 0.0001) return;
        }
        throw new AssertionError("Embedding should contain non-zero values");
    }

    static String encodeFloatsToBase64(int dimensions, int batchOffset) {
        var buffer = ByteBuffer.allocate(dimensions * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < dimensions; i++) buffer.putFloat((float) Math.sin((i + batchOffset) * 0.1));
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    static String encodeBytesToBase64(int dimensions) {
        var bytes = new byte[dimensions];
        for (int i = 0; i < dimensions; i++) bytes[i] = (byte) (i % 128);
        return Base64.getEncoder().encodeToString(bytes);
    }

    /** Builds a JSON response body with one or more base64-encoded embeddings (OpenAI/VoyageAI shape). */
    static String createBase64EmbeddingResponse(String... base64Embeddings) {
        var dataEntries = new StringBuilder();
        for (int i = 0; i < base64Embeddings.length; i++) {
            if (i > 0) dataEntries.append(",");
            dataEntries.append(Text.format("{\"object\":\"embedding\",\"embedding\":\"%s\",\"index\":%d}",
                    base64Embeddings[i], i));
        }
        return Text.format("""
                {"object":"list","data":[%s],"usage":{"total_tokens":%d}}
                """, dataEntries, base64Embeddings.length * 10);
    }
}
