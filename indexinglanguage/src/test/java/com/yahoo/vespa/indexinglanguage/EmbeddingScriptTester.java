package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.indexinglanguage.expressions.ExecutionContext;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.VerificationContext;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EmbeddingScriptTester {

    private final Map<String, Embedder> embedders;

    public EmbeddingScriptTester(Map<String, Embedder> embedders) {
        this.embedders = embedders;
    }

    public void testStatement(String expressionString, String input, String expected) {
        var expression = expressionFrom(expressionString);
        TensorType tensorType = TensorType.fromSpec("tensor(d[4])");

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        adapter.createField(new Field("myText", DataType.STRING));
        var tensorField = new Field("myTensor", new TensorDataType(tensorType));
        adapter.createField(tensorField);
        if (input != null)
            adapter.setValue("myText", new StringFieldValue(input));
        expression.setStatementOutput(new DocumentType("myDocument"), tensorField);

        // Necessary to resolve output type
        VerificationContext verificationContext = new VerificationContext(adapter);
        assertEquals(TensorDataType.class, expression.verify(verificationContext).getClass());

        ExecutionContext context = new ExecutionContext(adapter);
        expression.execute(context);
        if (input == null) {
            assertFalse(adapter.values.containsKey("myTensor"));
        }
        else {
            assertTrue(adapter.values.containsKey("myTensor"));
            assertEquals(Tensor.from(tensorType, expected),
                         ((TensorFieldValue) adapter.values.get("myTensor")).getTensor().get());
        }
    }

    public void testStatementThrows(String expressionString, String input, String expectedMessage) {
        try {
            testStatement(expressionString, input, null);
            fail();
        } catch (IllegalStateException e) {
            assertEquals(expectedMessage, e.getMessage());
        }
    }

    public Expression expressionFrom(String string) {
        try {
            return Expression.fromString(string, new SimpleLinguistics(), embedders);
        }
        catch (ParseException e) {
            throw new IllegalArgumentException(e);
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
