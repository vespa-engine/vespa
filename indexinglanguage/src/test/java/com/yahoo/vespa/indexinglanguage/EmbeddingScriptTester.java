package com.yahoo.vespa.indexinglanguage;

import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class EmbeddingScriptTester {

    public static void assertThrows(Runnable r, String expectedMessage) {
        try {
            r.run();
            fail();
        } catch (IllegalStateException e) {
            assertEquals(expectedMessage, e.getMessage());
        }
    }

    public static abstract class MockEmbedder implements Embedder {

        final String expectedDestination;
        final int addition;

        public MockEmbedder(String expectedDestination, int addition) {
            this.expectedDestination = expectedDestination;
            this.addition = addition;
        }

        @Override
        public List<Integer> embed(String text, Embedder.Context context) {
            return null;
        }

        void verifyDestination(Embedder.Context context) {
            assertEquals(expectedDestination, context.getDestination());
        }

    }

    /** An embedder which returns the char value of each letter in the input as a 1d indexed tensor. */
    public static class MockIndexedEmbedder extends MockEmbedder {

        public MockIndexedEmbedder(String expectedDestination) {
            this(expectedDestination, 0);
        }

        public MockIndexedEmbedder(String expectedDestination, int addition) {
            super(expectedDestination, addition);
        }

        @Override
        public Tensor embed(String text, Embedder.Context context, TensorType tensorType) {
            verifyDestination(context);
            var b = Tensor.Builder.of(tensorType);
            for (int i = 0; i < tensorType.dimensions().get(0).size().get(); i++)
                b.cell(i < text.length() ? text.charAt(i) + addition : 0, i);
            return b.build();
        }

    }

    /** An embedder which returns the char value of each letter in the input as a 1d mapped tensor. */
    public static class MockMappedEmbedder extends MockEmbedder {

        public MockMappedEmbedder(String expectedDestination) {
            this(expectedDestination, 0);
        }

        public MockMappedEmbedder(String expectedDestination, int addition) {
            super(expectedDestination, addition);
        }

        @Override
        public Tensor embed(String text, Embedder.Context context, TensorType tensorType) {
            verifyDestination(context);
            context.putCachedValue("myCacheKey", "myCachedValue");
            var b = Tensor.Builder.of(tensorType);
            for (int i = 0; i < text.length(); i++)
                b.cell().label(tensorType.dimensions().get(0).name(), text.charAt(i)).value(text.charAt(i) + addition);
            return b.build();
        }

    }

    /**
     * An embedder which returns the char value of each letter in the input as a 2d mixed tensor where each input
     * char becomes an indexed dimension containing input-1, input, input+1.
     */
    public static class MockMixedEmbedder extends MockEmbedder {

        public MockMixedEmbedder(String expectedDestination) {
            this(expectedDestination, 0);
        }

        public MockMixedEmbedder(String expectedDestination, int addition) {
            super(expectedDestination, addition);
        }

        @Override
        public Tensor embed(String text, Embedder.Context context, TensorType tensorType) {
            verifyDestination(context);
            var b = Tensor.Builder.of(tensorType);
            String mappedDimension = tensorType.mappedSubtype().dimensions().get(0).name();
            String indexedDimension = tensorType.indexedSubtype().dimensions().get(0).name();
            for (int i = 0; i < text.length(); i++) {
                for (int j = 0; j < 3; j++) {
                    b.cell().label(mappedDimension, i)
                            .label(indexedDimension, j)
                            .value(text.charAt(i) + addition + j - 1);
                }
            }
            return b.build();
        }
    }

}
