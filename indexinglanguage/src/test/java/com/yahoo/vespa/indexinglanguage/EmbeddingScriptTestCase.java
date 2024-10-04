// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.indexinglanguage.expressions.ExecutionContext;
import com.yahoo.vespa.indexinglanguage.expressions.VerificationContext;
import com.yahoo.vespa.indexinglanguage.expressions.VerificationException;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class EmbeddingScriptTestCase {

    @Test
    public void testEmbed() {
        // No embedders - parsing only
        var tester = new EmbeddingScriptTester(Embedder.throwsOnUse.asMap());
        tester.expressionFrom("input myText | embed emb1 | attribute 'myTensor'");

        // One embedder
        tester = new EmbeddingScriptTester(Map.of("emb1", new EmbeddingScriptTester.MockIndexedEmbedder("myDocument.myTensor")));
        tester.testStatement("input myText | embed | attribute 'myTensor'", "input text", "[105, 110, 112, 117]");
        tester.testStatement("input myText | embed emb1 | attribute 'myTensor'", "input text", "[105, 110, 112, 117]");
        tester.testStatement("input myText | embed 'emb1' | attribute 'myTensor'", "input text", "[105, 110, 112, 117]");
        tester.testStatement("input myText | embed 'emb1' | attribute 'myTensor'", null, null);

        // Two embedders
        tester = new EmbeddingScriptTester(Map.of("emb1", new EmbeddingScriptTester.MockIndexedEmbedder("myDocument.myTensor"),
                                                  "emb2", new EmbeddingScriptTester.MockIndexedEmbedder("myDocument.myTensor", 1)));
        tester.testStatement("input myText | embed emb1 | attribute 'myTensor'", "my input", "[109.0, 121.0, 32.0, 105.0]");
        tester.testStatement("input myText | embed emb2 | attribute 'myTensor'", "my input", "[110.0, 122.0, 33.0, 106.0]");
        tester.testStatementThrows("input myText | embed | attribute 'myTensor'", "input text",
                                   "Multiple embedders are provided but no embedder id is given. Valid embedders are emb1, emb2");
        tester.testStatementThrows("input myText | embed emb3 | attribute 'myTensor'", "input text",
                                   "Can't find embedder 'emb3'. Valid embedders are emb1, emb2");
    }

    @Test
    public void testEmbedAndBinarize() {
        var tester = new EmbeddingScriptTester(Map.of("emb1", new EmbeddingScriptTester.MockIndexedEmbedder("myDocument.myTensor", -111)));
        tester.testStatement("input myText | embed | binarize | attribute 'myTensor'", "input text", "[0, 0, 1, 1]");
        tester.testStatement("input myText | embed | binarize 3.0 | attribute 'myTensor'", "input text", "[0, 0, 0, 1]");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testArrayEmbed() {
        var tester = new EmbeddingScriptTester(Map.of("emb1", new EmbeddingScriptTester.MockIndexedEmbedder("myDocument.myTensorArray")));

        TensorType tensorType = TensorType.fromSpec("tensor(d[4])");
        var expression = tester.expressionFrom("input myTextArray | for_each { embed } | attribute 'myTensorArray'");

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        adapter.createField(new Field("myTextArray", new ArrayDataType(DataType.STRING)));

        var tensorField = new Field("myTensorArray", new ArrayDataType(new TensorDataType(tensorType)));
        adapter.createField(tensorField);

        var array = new Array<StringFieldValue>(new ArrayDataType(DataType.STRING));
        array.add(new StringFieldValue("first"));
        array.add(new StringFieldValue("second"));
        adapter.setValue("myTextArray", array);
        expression.setStatementOutput(new DocumentType("myDocument"), tensorField);

        // Necessary to resolve output type
        VerificationContext verificationContext = new VerificationContext(adapter);
        assertEquals(new ArrayDataType(new TensorDataType(tensorType)), expression.verify(verificationContext));

        ExecutionContext context = new ExecutionContext(adapter);
        context.setValue(array);
        expression.execute(context);
        assertTrue(adapter.values.containsKey("myTensorArray"));
        var tensorArray = (Array<TensorFieldValue>)adapter.values.get("myTensorArray");
        assertEquals(Tensor.from(tensorType, "[102, 105, 114, 115]"), tensorArray.get(0).getTensor().get());
        assertEquals(Tensor.from(tensorType, "[115, 101,  99, 111]"), tensorArray.get(1).getTensor().get());
    }

    @Test
    public void testArrayEmbedWithConcatenation() {
        var tester = new EmbeddingScriptTester(Map.of("emb1", new EmbeddingScriptTester.MockIndexedEmbedder("myDocument.mySparseTensor")));

        TensorType tensorType = TensorType.fromSpec("tensor(passage{}, d[4])");
        var expression = tester.expressionFrom("input myTextArray | for_each { input title . \" \" . _ } | embed | attribute 'mySparseTensor'");

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        adapter.createField(new Field("myTextArray", new ArrayDataType(DataType.STRING)));

        var tensorField = new Field("mySparseTensor", new TensorDataType(tensorType));
        adapter.createField(tensorField);

        var array = new Array<StringFieldValue>(new ArrayDataType(DataType.STRING));
        array.add(new StringFieldValue("first"));
        array.add(new StringFieldValue("second"));
        adapter.setValue("myTextArray", array);

        var titleField = new Field("title", DataType.STRING);
        adapter.createField(titleField);
        adapter.setValue("title", new StringFieldValue("title1"));

        expression.setStatementOutput(new DocumentType("myDocument"), tensorField);

        // Necessary to resolve output type
        VerificationContext verificationContext = new VerificationContext(adapter);
        assertEquals(new TensorDataType(tensorType), expression.verify(verificationContext));

        ExecutionContext context = new ExecutionContext(adapter);
        context.setValue(array);
        expression.execute(context);
        assertTrue(adapter.values.containsKey("mySparseTensor"));
        var sparseTensor = (TensorFieldValue)adapter.values.get("mySparseTensor");
        assertEquals(Tensor.from(tensorType, "{ '0':[116.0, 105.0, 116.0, 108.0], 1:[116.0, 105.0, 116.0, 108.0]}"),
                     sparseTensor.getTensor().get());
    }

    /** Multiple paragraphs */
    @Test
    public void testArrayEmbedTo2dMixedTensor() {
        var tester = new EmbeddingScriptTester(Map.of("emb1", new EmbeddingScriptTester.MockIndexedEmbedder("myDocument.mySparseTensor")));

        TensorType tensorType = TensorType.fromSpec("tensor(passage{}, d[4])");
        var expression = tester.expressionFrom("input myTextArray | embed | attribute 'mySparseTensor'");

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        adapter.createField(new Field("myTextArray", new ArrayDataType(DataType.STRING)));

        var tensorField = new Field("mySparseTensor", new TensorDataType(tensorType));
        adapter.createField(tensorField);

        var array = new Array<StringFieldValue>(new ArrayDataType(DataType.STRING));
        array.add(new StringFieldValue("first"));
        array.add(new StringFieldValue("second"));
        adapter.setValue("myTextArray", array);
        expression.setStatementOutput(new DocumentType("myDocument"), tensorField);

        // Necessary to resolve output type
        VerificationContext verificationContext = new VerificationContext(adapter);
        assertEquals(new TensorDataType(tensorType), expression.verify(verificationContext));

        ExecutionContext context = new ExecutionContext(adapter);
        context.setValue(array);
        expression.execute(context);
        assertTrue(adapter.values.containsKey("mySparseTensor"));
        var sparseTensor = (TensorFieldValue)adapter.values.get("mySparseTensor");
        assertEquals(Tensor.from(tensorType, "{ '0':[102, 105, 114, 115], '1':[115, 101,  99, 111]}"),
                     sparseTensor.getTensor().get());
    }

    /** Multiple paragraphs, and each paragraph leading to multiple vectors (ColBert style) */
    @Test
    public void testArrayEmbedTo3dMixedTensor() {
        var tester = new EmbeddingScriptTester(Map.of("emb1", new EmbeddingScriptTester.MockMixedEmbedder("myDocument.mySparseTensor")));

        TensorType tensorType = TensorType.fromSpec("tensor(passage{}, token{}, d[3])");
        var expression = tester.expressionFrom("input myTextArray | embed emb1 passage | attribute 'mySparseTensor'");
        assertEquals("input myTextArray | embed emb1 passage | attribute mySparseTensor", expression.toString());

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        adapter.createField(new Field("myTextArray", new ArrayDataType(DataType.STRING)));
        var tensorField = new Field("mySparseTensor", new TensorDataType(tensorType));
        adapter.createField(tensorField);

        var array = new Array<StringFieldValue>(new ArrayDataType(DataType.STRING));
        array.add(new StringFieldValue("first"));
        array.add(new StringFieldValue("sec"));
        adapter.setValue("myTextArray", array);
        expression.setStatementOutput(new DocumentType("myDocument"), tensorField);

        assertEquals(new TensorDataType(tensorType), expression.verify(new VerificationContext(adapter)));

        ExecutionContext context = new ExecutionContext(adapter);
        context.setValue(array);
        expression.execute(context);
        assertTrue(adapter.values.containsKey("mySparseTensor"));
        var sparseTensor = (TensorFieldValue)adapter.values.get("mySparseTensor");
        // The two "passages" are [first, sec], the middle (d=1) token encodes those letters
        assertEquals(Tensor.from(tensorType,
                        """
                        {
                        {passage:0, token:0, d:0}: 101,
                        {passage:0, token:0, d:1}: 102,
                        {passage:0, token:0, d:2}: 103,
                        {passage:0, token:1, d:0}: 104,
                        {passage:0, token:1, d:1}: 105,
                        {passage:0, token:1, d:2}: 106,
                        {passage:0, token:2, d:0}: 113,
                        {passage:0, token:2, d:1}: 114,
                        {passage:0, token:2, d:2}: 115,
                        {passage:0, token:3, d:0}: 114,
                        {passage:0, token:3, d:1}: 115,
                        {passage:0, token:3, d:2}: 116,
                        {passage:0, token:4, d:0}: 115,
                        {passage:0, token:4, d:1}: 116,
                        {passage:0, token:4, d:2}: 117,
                        {passage:1, token:0, d:0}: 114,
                        {passage:1, token:0, d:1}: 115,
                        {passage:1, token:0, d:2}: 116,
                        {passage:1, token:1, d:0}: 100,
                        {passage:1, token:1, d:1}: 101,
                        {passage:1, token:1, d:2}: 102,
                        {passage:1, token:2, d:0}:  98,
                        {passage:1, token:2, d:1}:  99,
                        {passage:1, token:2, d:2}: 100
                        }
                        """),
                sparseTensor.getTensor().get());
    }

    /** Multiple paragraphs, and each paragraph leading to multiple vectors (ColBERT style) */
    @Test
    public void testArrayEmbedTo3dMixedTensor_missingDimensionArgument() {
        var tester = new EmbeddingScriptTester(Map.of("emb1", new EmbeddingScriptTester.MockMixedEmbedder("myDocument.mySparseTensor")));

        TensorType tensorType = TensorType.fromSpec("tensor(passage{}, token{}, d[3])");
        var expression = tester.expressionFrom("input myTextArray | embed emb1 | attribute 'mySparseTensor'");

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        adapter.createField(new Field("myTextArray", new ArrayDataType(DataType.STRING)));
        adapter.createField(new Field("mySparseTensor", new TensorDataType(tensorType)));

        try {
            expression.verify(new VerificationContext(adapter));
            fail("Expected exception");
        }
        catch (VerificationException e) {
            assertEquals("When the embedding target field is a 3d tensor the name of the tensor dimension that corresponds to the input array elements must be given as a second argument to embed, e.g: ... | embed colbert paragraph | ...",
                         e.getMessage());
        }
    }

    /** Multiple paragraphs, and each paragraph leading to multiple vectors (ColBert style) */
    @Test
    public void testArrayEmbedTo3dMixedTensor_wrongDimensionArgument() {
        var tester = new EmbeddingScriptTester(Map.of("emb1", new EmbeddingScriptTester.MockMixedEmbedder("myDocument.mySparseTensor")));

        TensorType tensorType = TensorType.fromSpec("tensor(passage{}, token{}, d[3])");
        var expression = tester.expressionFrom("input myTextArray | embed emb1 d | attribute 'mySparseTensor'");

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        adapter.createField(new Field("myTextArray", new ArrayDataType(DataType.STRING)));
        adapter.createField(new Field("mySparseTensor", new TensorDataType(tensorType)));

        try {
            expression.verify(new VerificationContext(adapter));
            fail("Expected exception");
        }
        catch (VerificationException e) {
            assertEquals("The dimension 'd' given to embed is not a sparse dimension of the target type tensor(d[3],passage{},token{})",
                         e.getMessage());
        }
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    public void testEmbedToSparseTensor() {
        var tester = new EmbeddingScriptTester(Map.of("emb1", new EmbeddingScriptTester.MockMappedEmbedder("myDocument.mySparseTensor", 0)));

        TensorType tensorType = TensorType.fromSpec("tensor(t{})");
        var expression = tester.expressionFrom("input text | embed | attribute 'mySparseTensor'");

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        adapter.createField(new Field("text", DataType.STRING));

        var tensorField = new Field("mySparseTensor", new TensorDataType(tensorType));
        adapter.createField(tensorField);

        var text = new StringFieldValue("abc");
        adapter.setValue("text", text);
        expression.setStatementOutput(new DocumentType("myDocument"), tensorField);

        // Necessary to resolve output type
        VerificationContext verificationContext = new VerificationContext(adapter);
        assertEquals(new TensorDataType(tensorType), expression.verify(verificationContext));

        ExecutionContext context = new ExecutionContext(adapter);
        context.setValue(text);
        expression.execute(context);
        assertTrue(adapter.values.containsKey("mySparseTensor"));
        var sparseTensor = (TensorFieldValue)adapter.values.get("mySparseTensor");
        assertEquals(Tensor.from(tensorType, "tensor(t{}):{97:97.0, 98:98.0, 99:99.0}"),
                     sparseTensor.getTensor().get());
        assertEquals("Cached value always set by MockMappedEmbedder is present",
                     "myCachedValue", context.getCachedValue("myCacheKey"));
    }

    /** Multiple paragraphs with sparse encoding (splade style) */
    @Test
    public void testArrayEmbedTo2dMappedTensor_wrongDimensionArgument() {
        var tester = new EmbeddingScriptTester(Map.of("emb1", new EmbeddingScriptTester.MockMappedEmbedder("myDocument.my2DSparseTensor")));

        TensorType tensorType = TensorType.fromSpec("tensor(passage{}, token{})");
        var expression = tester.expressionFrom("input myTextArray | embed emb1 doh | attribute 'my2DSparseTensor'");

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        adapter.createField(new Field("myTextArray", new ArrayDataType(DataType.STRING)));
        adapter.createField(new Field("my2DSparseTensor", new TensorDataType(tensorType)));

        try {
            expression.verify(new VerificationContext(adapter));
            fail("Expected exception");
        }
        catch (VerificationException e) {
            assertEquals("The dimension 'doh' given to embed is not a sparse dimension of the target type tensor(passage{},token{})",
                         e.getMessage());
        }
    }

    /** Multiple paragraphs with sparse encoding (splade style) */
    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void testArrayEmbedTo2MappedTensor() {
        var tester = new EmbeddingScriptTester(Map.of("emb1", new EmbeddingScriptTester.MockMappedEmbedder("myDocument.my2DSparseTensor")));

        TensorType tensorType = TensorType.fromSpec("tensor(passage{}, token{})");
        var expression = tester.expressionFrom("input myTextArray | embed emb1 passage | attribute 'my2DSparseTensor'");
        assertEquals("input myTextArray | embed emb1 passage | attribute my2DSparseTensor", expression.toString());

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        adapter.createField(new Field("myTextArray", new ArrayDataType(DataType.STRING)));
        var tensorField = new Field("my2DSparseTensor", new TensorDataType(tensorType));
        adapter.createField(tensorField);

        var array = new Array<StringFieldValue>(new ArrayDataType(DataType.STRING));
        array.add(new StringFieldValue("abc"));
        array.add(new StringFieldValue("cde"));
        adapter.setValue("myTextArray", array);
        expression.setStatementOutput(new DocumentType("myDocument"), tensorField);

        assertEquals(new TensorDataType(tensorType), expression.verify(new VerificationContext(adapter)));

        ExecutionContext context = new ExecutionContext(adapter);
        context.setValue(array);
        expression.execute(context);
        assertTrue(adapter.values.containsKey("my2DSparseTensor"));
        var sparse2DTensor = (TensorFieldValue)adapter.values.get("my2DSparseTensor");
        assertEquals(Tensor.from(
                        tensorType,
                        "tensor(passage{},token{}):" +
                                "{{passage:0,token:97}:97.0, " +
                                "{passage:0,token:98}:98.0, " +
                                "{passage:0,token:99}:99.0, " +
                                "{passage:1,token:100}:100.0, " +
                                "{passage:1,token:101}:101.0, " +
                                "{passage:1,token:99}:99.0}"),
                sparse2DTensor.getTensor().get());
    }

}
