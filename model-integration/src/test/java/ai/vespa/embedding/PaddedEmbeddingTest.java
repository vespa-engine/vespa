// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.modelintegration.evaluator.EmbeddedOnnxRuntime;
import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import ai.vespa.modelintegration.utils.ModelPathHelper;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.ModelReference;
import com.yahoo.embedding.BertBaseEmbedderConfig;
import com.yahoo.embedding.ColBertEmbedderConfig;
import com.yahoo.embedding.SpladeEmbedderConfig;
import com.yahoo.embedding.huggingface.HuggingFaceEmbedderConfig;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Embeds with each ONNX embedder through an OnnxPadder over the embedded runtime, and checks that the embeddings
 * are the same as without padding, and that the model saw inputs padded to the buckets.
 * Pooling must follow the real tokens, not the padded, which the cut back outputs ensure.
 * ColBERT keeps one vector per token, so its embeddings show whether the padding is cut back: queries are
 * expanded with [MASK] tokens to a fixed length, with attention mask zeros, before padding, and documents vary in
 * length. Does not need Triton, see TritonPaddedEmbeddingTest for the same through it.
 *
 * @author glebashnik
 */
class PaddedEmbeddingTest {

    private static final String MODELS = "src/test/models/onnx/transformer/";
    private static final String TOKENIZER = MODELS + "real_tokenizer.json";
    /** A float model with BERT-like masked attention, whose output for the real tokens is unchanged by padding */
    private static final String MASKED_MODEL = MODELS + "dummy_masked_attention_embedder.onnx";
    private static final String DOCUMENT = "schema.indexing";
    private static final String QUERY = "query(qt{})";
    private static final TensorType COLBERT_FLOAT = TensorType.fromSpec("tensor<float>(dt{},x[16])");
    private static final TensorType COLBERT_INT8 = TensorType.fromSpec("tensor<int8>(dt{},x[2])");
    private static final String SHORT_TEXT = "This is a test with lots of input text here";
    private static final String LONG_TEXT = SHORT_TEXT.repeat(4);
    private static final String LONGER_THAN_CONFIGURED_BUCKETS = SHORT_TEXT.repeat(10);

    @Test
    void huggingFaceEmbedsTheSameWithPaddingForEveryPooling() {
        for (var pooling : HuggingFaceEmbedderConfig.PoolingStrategy.Enum.values()) {
            if (pooling == HuggingFaceEmbedderConfig.PoolingStrategy.Enum.none) continue; // Needs a model output without tokens
            var config = new HuggingFaceEmbedderConfig.Builder()
                    .tokenizerPath(ModelReference.valueOf(TOKENIZER))
                    .transformerModel(ModelReference.valueOf(MASKED_MODEL))
                    .poolingStrategy(pooling)
                    .build();
            assertEmbedsTheSame(runtime -> new HuggingFaceEmbedder(runtime, Embedder.Runtime.testInstance(), config, PaddingTestRuntime.onnxConfig(runtime), new ModelPaths()),
                                List.of("tensor<float>(x[16])", "tensor<int8>(x[2])"), "Pooling " + pooling);
        }
    }

    @Test
    void spladeEmbedsTheSameWithPadding() {
        var config = new SpladeEmbedderConfig.Builder()
                .tokenizerPath(ModelReference.valueOf(TOKENIZER))
                .transformerModel(ModelReference.valueOf(MODELS + "dummy_transformer_mlm.onnx"))
                .termScoreThreshold(1.15)
                .build();
        assertEmbedsTheSame(runtime -> new SpladeEmbedder(runtime, Embedder.Runtime.testInstance(), config, PaddingTestRuntime.onnxConfig(runtime)),
                            List.of("tensor<float>(t{})"), "SPLADE");
    }

    @Test
    void bertBaseEmbedsTheSameWithPadding() {
        var config = new BertBaseEmbedderConfig.Builder()
                .tokenizerVocab(ModelReference.valueOf(MODELS + "dummy_vocab.txt"))
                .transformerModel(ModelReference.valueOf(MASKED_MODEL))
                .transformerTokenTypeIds("")
                .transformerOutput("last_hidden_state")
                .build();
        // The dummy vocabulary has single letters
        assertEmbedsTheSame(runtime -> new BertBaseEmbedder(runtime, Embedder.Runtime.testInstance(), config, PaddingTestRuntime.onnxConfig(runtime)),
                            List.of("tensor<float>(x[16])"), "BERT", "a b c d e f g h i j k", "a b c d e f g h i j k ".repeat(4), "a b c d e f g h i j k ".repeat(10));
    }

    @Test
    void theColBertPaddedSequenceLengthIsTheExpandedQueryPadded() {
        var runtime = new PaddingTestRuntime();
        var metrics = new LengthRecordingRuntime();
        var padded = colBert(runtime, metrics, MASKED_MODEL, true, 32, 512);
        try {
            embed(padded, SHORT_TEXT, QUERY, COLBERT_FLOAT);
            assertEquals(runtime.lastInputLength(), metrics.lastPaddedSequenceLength(), "The bucket the expanded query is padded to");
            assertTrue(metrics.lastSequenceLength() < 32, "The text has fewer tokens than the expanded query");
        } finally {
            padded.deconstruct();
        }
    }

    @Test
    void colBertDocumentsArePaddedToTheSmallestBucketThatFitsAndEmbedTheSame() {
        var runtime = new PaddingTestRuntime();
        var padded = colBert(runtime, MASKED_MODEL, true);
        var unpadded = colBert(EmbeddedOnnxRuntime.createTestInstance(), MASKED_MODEL, true);
        try {
            for (var type : List.of(COLBERT_FLOAT, COLBERT_INT8)) {
                var expected = embed(unpadded, SHORT_TEXT, DOCUMENT, type);
                assertClose(expected, embed(padded, SHORT_TEXT, DOCUMENT, type), "ColBERT");
                assertEquals(16, runtime.lastInputLength(), "A short document is padded to the first bucket");
                assertTrue(tokensIn(expected) < 16, "One vector per real token, none for the padding");
            }

            var expected = embed(unpadded, LONG_TEXT, DOCUMENT, COLBERT_FLOAT);
            assertClose(expected, embed(padded, LONG_TEXT, DOCUMENT, COLBERT_FLOAT), "ColBERT");
            assertEquals(64, runtime.lastInputLength(), "A longer document is padded to the next bucket");
            assertTrue(tokensIn(expected) > 16 && tokensIn(expected) < 64);

            expected = embed(unpadded, LONGER_THAN_CONFIGURED_BUCKETS, DOCUMENT, COLBERT_FLOAT);
            assertClose(expected, embed(padded, LONGER_THAN_CONFIGURED_BUCKETS, DOCUMENT, COLBERT_FLOAT), "ColBERT");
            assertEquals(tokensIn(expected), runtime.lastInputLength(), "Longer than the largest bucket: not padded");
            assertTrue(tokensIn(expected) > 64, "Longer than the configured buckets");
        } finally {
            padded.deconstruct();
            unpadded.deconstruct();
        }
    }

    @Test
    void colBertExpandedQueriesArePaddedToTheSmallestBucketThatFitsAndEmbedTheSame() {
        var runtime = new PaddingTestRuntime();
        var padded = colBert(runtime, MASKED_MODEL, true);
        var unpadded = colBert(EmbeddedOnnxRuntime.createTestInstance(), MASKED_MODEL, true);
        try {
            assertEquals(PaddingTestRuntime.BUCKETS, runtime.buckets(), "The configured buckets");
            var expected = embed(unpadded, SHORT_TEXT, QUERY, COLBERT_FLOAT);
            assertClose(expected, embed(padded, SHORT_TEXT, QUERY, COLBERT_FLOAT), "ColBERT");
            assertEquals(64, runtime.lastInputLength(), "The query is expanded to 32 tokens by the embedder, then padded to the next bucket");
            assertEquals(32, tokensIn(expected), "One vector per query token, including the expansion");
        } finally {
            padded.deconstruct();
            unpadded.deconstruct();
        }
    }

    @Test
    void colBertBucketsLongerThanItsMaxTokensAreTrimmedToThem() {
        var runtime = new PaddingTestRuntime();
        var padded = colBert(runtime, MASKED_MODEL, true, 32, 48);
        var unpadded = colBert(EmbeddedOnnxRuntime.createTestInstance(), MASKED_MODEL, true, 32, 48);
        try {
            assertEquals(List.of(16, 48), runtime.buckets(), "The configured 64 is longer than any input, so it is trimmed to the max document tokens");
            var expected = embed(unpadded, LONGER_THAN_CONFIGURED_BUCKETS, DOCUMENT, COLBERT_FLOAT);
            assertClose(expected, embed(padded, LONGER_THAN_CONFIGURED_BUCKETS, DOCUMENT, COLBERT_FLOAT), "ColBERT");
            assertEquals(48, runtime.lastInputLength());
            assertEquals(48, tokensIn(expected), "Truncated to the max document tokens");
        } finally {
            padded.deconstruct();
            unpadded.deconstruct();
        }
    }

    @Test
    void colBertQueriesWithoutExpansionTokensInTheResultEmbedTheSame() {
        var runtime = new PaddingTestRuntime();
        var padded = colBert(runtime, MASKED_MODEL, false);
        var unpadded = colBert(EmbeddedOnnxRuntime.createTestInstance(), MASKED_MODEL, false);
        try {
            var expected = embed(unpadded, SHORT_TEXT, QUERY, COLBERT_FLOAT);
            assertClose(expected, embed(padded, SHORT_TEXT, QUERY, COLBERT_FLOAT), "ColBERT");
            assertEquals(64, runtime.lastInputLength(), "The query is expanded to 32 tokens, then padded to the next bucket");
            assertTrue(tokensIn(expected) < 32, "Only the real query tokens");
        } finally {
            padded.deconstruct();
            unpadded.deconstruct();
        }
    }

    /** The ColBERT dummy model attends to every token, so padding changes its output and must not be used */
    @Test
    void paddingIsDisabledForAModelWhichAttendsToThePaddingAndDocumentsStillEmbedTheSame() {
        var runtime = new PaddingTestRuntime();
        var padded = colBert(runtime, MODELS + "colbert-dummy-v2.onnx", true);
        var unpadded = colBert(EmbeddedOnnxRuntime.createTestInstance(), MODELS + "colbert-dummy-v2.onnx", true);
        try {
            assertFalse(runtime.isPadding());
            for (var context : List.of(DOCUMENT, QUERY)) {
                var expected = embed(unpadded, SHORT_TEXT, context, COLBERT_FLOAT);
                assertClose(expected, embed(padded, SHORT_TEXT, context, COLBERT_FLOAT), "ColBERT");
                assertEquals(tokensIn(expected), runtime.lastInputLength(), "Not padded");
            }
        } finally {
            padded.deconstruct();
            unpadded.deconstruct();
        }
    }

    private static void assertEmbedsTheSame(Function<OnnxRuntime, Embedder> embedderOf, List<String> types, String what) {
        assertEmbedsTheSame(embedderOf, types, what, SHORT_TEXT, LONG_TEXT, LONGER_THAN_CONFIGURED_BUCKETS);
    }

    /** Embeds the texts padded and unpadded, and checks the embeddings are the same and the model saw each bucket. */
    private static void assertEmbedsTheSame(Function<OnnxRuntime, Embedder> embedderOf, List<String> types, String what,
                                            String shortText, String longText, String longerThanConfiguredBuckets) {
        var runtime = new PaddingTestRuntime();
        var padded = embedderOf.apply(runtime);
        var unpaddedRuntime = PaddingTestRuntime.unpadded();
        var unpadded = embedderOf.apply(unpaddedRuntime);
        try {
            assertTrue(runtime.isPadding(), what + " should be padded");
            assertEquals(PaddingTestRuntime.BUCKETS, runtime.buckets(), what + ": the configured buckets");
            var texts = List.of(shortText, longText, longerThanConfiguredBuckets);
            for (int i = 0; i < texts.size(); i++) {
                for (var type : types) {
                    var targetType = TensorType.fromSpec(type);
                    assertClose(embed(unpadded, texts.get(i), DOCUMENT, targetType), embed(padded, texts.get(i), DOCUMENT, targetType), what);
                    if (i < PaddingTestRuntime.BUCKETS.size())
                        assertEquals(PaddingTestRuntime.BUCKETS.get(i), runtime.lastInputLength(), what + ": text " + i + " as " + type);
                    else
                        assertEquals(unpaddedRuntime.lastInputLength(), runtime.lastInputLength(), what + ": text " + i + " as " + type + " is longer than the largest bucket, so not padded");
                }
            }
        } finally {
            ((AbstractComponent) padded).deconstruct();
            ((AbstractComponent) unpadded).deconstruct();
        }
    }

    private static ColBertEmbedder colBert(OnnxRuntime runtime, String model, boolean attendToExpansionTokens) {
        return colBert(runtime, model, attendToExpansionTokens, 32);
    }

    private static ColBertEmbedder colBert(OnnxRuntime runtime, String model, boolean attendToExpansionTokens, int maxQueryTokens) {
        return colBert(runtime, model, attendToExpansionTokens, maxQueryTokens, 512);
    }

    private static ColBertEmbedder colBert(OnnxRuntime runtime, String model, boolean attendToExpansionTokens, int maxQueryTokens, int maxDocumentTokens) {
        return colBert(runtime, Embedder.Runtime.testInstance(), model, attendToExpansionTokens, maxQueryTokens, maxDocumentTokens);
    }

    private static ColBertEmbedder colBert(OnnxRuntime runtime, Embedder.Runtime metrics, String model, boolean attendToExpansionTokens,
                                           int maxQueryTokens, int maxDocumentTokens) {
        var config = new ColBertEmbedderConfig.Builder()
                .tokenizerPath(ModelReference.valueOf(TOKENIZER))
                .transformerModel(ModelReference.valueOf(model))
                .attendToExpansionTokens(attendToExpansionTokens)
                .maxQueryTokens(maxQueryTokens)
                .maxDocumentTokens(maxDocumentTokens);
        if (model.equals(MASKED_MODEL)) config.transformerOutput("last_hidden_state");
        return new ColBertEmbedder(runtime, metrics, config.build(), PaddingTestRuntime.onnxConfig(runtime));
    }

    /** Embeds with a new context each time, since a context caches embeddings by embedder id and text, which the embedders share */
    private static Tensor embed(Embedder embedder, String text, String destination, TensorType type) {
        return embedder.embed(text, new Embedder.Context(destination), type);
    }

    /** The number of token vectors in a ColBERT embedding */
    private static long tokensIn(Tensor embedding) {
        return embedding.size() / embedding.type().indexedSubtype().dimensions().get(0).size().orElseThrow();
    }

    private static void assertClose(Tensor expected, Tensor actual, String what) {
        assertEquals(expected.type(), actual.type(), what);
        assertEquals(expected.size(), actual.size(), what + ": cells of " + expected + " and " + actual);
        expected.cellIterator().forEachRemaining(cell -> {
            double value = actual.get(cell.getKey());
            assertTrue(Math.abs(cell.getValue() - value) <= 1e-4, what + ": expected " + cell.getValue() + " at " + cell.getKey() + ", got " + value);
        });
    }

    /** Records the sequence lengths an embedder samples */
    private static class LengthRecordingRuntime implements Embedder.Runtime {

        final List<Long> sequenceLengths = new ArrayList<>();
        final List<Long> paddedSequenceLengths = new ArrayList<>();

        long lastSequenceLength() { return sequenceLengths.get(sequenceLengths.size() - 1); }
        long lastPaddedSequenceLength() { return paddedSequenceLengths.get(paddedSequenceLengths.size() - 1); }

        @Override public void sampleSequenceLength(long length, Embedder.Context ctx) { sequenceLengths.add(length); }
        @Override public void samplePaddedSequenceLength(long length, Embedder.Context ctx) { paddedSequenceLengths.add(length); }
        @Override public void sampleEmbeddingLatency(double millis, Embedder.Context ctx) { }
        @Override public void sampleRequestCount(Embedder.Context ctx) { }
        @Override public void sampleRequestFailure(Embedder.Context ctx, int statusCode) { }

    }

    private static class ModelPaths implements ModelPathHelper {
        @Override
        public Path getModelPathResolvingIfNecessary(ModelReference modelReference) { return modelReference.value(); }
    }

}
