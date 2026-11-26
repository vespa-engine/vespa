// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.Field;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.Raw;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.language.Language;
import com.yahoo.language.detect.Detection;
import com.yahoo.language.detect.Detector;
import com.yahoo.language.detect.Hint;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.InvalidInputException;
import com.yahoo.vespa.indexinglanguage.expressions.TypeContext;
import com.yahoo.vespa.indexinglanguage.expressions.VerificationException;
import com.yahoo.vespa.indexinglanguage.parser.IndexingInput;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yahoo.language.process.Embedder.Modality.IMAGE;
import static com.yahoo.language.process.Embedder.Modality.TEXT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Tests embedding of an image in a raw value, and of a text and an image as a multipart input:
 * 'input text, input image | embed'.
 *
 * @author glebashnik
 */
public class MultimodalEmbeddingScriptTestCase {

    private static final TensorType tensorType = TensorType.fromSpec("tensor(d[4])");
    /** The 3 byte signature of a JPEG file. */
    private static final byte[] image = new byte[] { (byte)0xFF, (byte)0xD8, (byte)0xFF };

    /** A tester with an embedder which accepts a text, an image, or both. */
    private final EmbeddingScriptTester tester = new EmbeddingScriptTester(Map.of("emb1", new InputEmbedder()));

    @Test
    public void testEmbedImage() {
        assertEmbedding("[0, 103, 0, 0]", "input myImage | embed | attribute 'myTensor'", null, image);
        assertEmbedding(null, "input myImage | embed | attribute 'myTensor'", null, null);
        assertEmbedding(null, "input myImage | embed | attribute 'myTensor'", null, new byte[0]);
    }

    @Test
    public void testRawArrayIsNotAccepted() {
        var expression = tester.expressionFrom("input myImages | embed | attribute 'myTensor'");
        var adapter = new SimpleTestAdapter();
        adapter.createField(new Field("myImages", new ArrayDataType(DataType.RAW)));
        var tensorField = new Field("myTensor", new TensorDataType(TensorType.fromSpec("tensor(image{},d[4])")));
        adapter.createField(tensorField);
        expression.setStatementOutput(new DocumentType("myDocument"), tensorField);

        var e = assertThrows(VerificationException.class, () -> expression.resolve(new TypeContext(adapter)));
        assertTrue(e.getMessage(),
                   e.getMessage().contains("This requires either a string, raw or array<string> input type, " +
                                           "or a string and a raw separated by a comma, but got Array<raw>"));
    }

    @Test
    public void testEmbedTextAndImage() {
        assertEmbedding("[5, 103, 0, 0]", "input myText, input myImage | embed | attribute 'myTensor'", "hello", image);
        // The order of the parts has no meaning
        assertEmbedding("[5, 103, 0, 0]", "input myImage, input myText | embed | attribute 'myTensor'", "hello", image);
    }

    @Test
    public void testPartsBindLooserThanConcatenationAndTighterThanPipe() {
        assertEmbedding("[11, 103, 0, 0]", "input myText . ' ' . input myText, input myImage | embed | attribute 'myTensor'", "hello", image);
        assertEmbedding("[2, 103, 0, 0]", "(input myText | substring 0 2), input myImage | embed | attribute 'myTensor'", "hello", image);
    }

    @Test
    public void testPartsMustBeOneStringAndOneRaw() {
        var e = assertThrows(VerificationException.class,
                             () -> embed(tester, "input myText, input myText | embed | attribute 'myTensor'", "hello", image));
        assertTrue(e.getMessage(), e.getMessage().contains("The parts to embed must be one string and one raw, " +
                                                           "but got 2 parts: [string, string]. " +
                                                           "Use '.' to join strings into one part"));

        e = assertThrows(VerificationException.class,
                         () -> embed(tester, "input myImage, input myImage | embed | attribute 'myTensor'", "hello", image));
        assertTrue(e.getMessage(), e.getMessage().contains("but got 2 parts: [raw, raw]"));
        assertFalse(e.getMessage(), e.getMessage().contains("to join strings"));

        e = assertThrows(VerificationException.class,
                         () -> embed(tester, "input myText, input myImage, input myText | embed | attribute 'myTensor'", "hello", image));
        assertTrue(e.getMessage(), e.getMessage().contains("but got 3 parts: [string, raw, string]"));

        e = assertThrows(VerificationException.class,
                         () -> embed(tester, "input myText, (input myText | to_int) | embed | attribute 'myTensor'", "1", image));
        assertTrue(e.getMessage(), e.getMessage().contains("Each part to embed must be a string or raw, but part 2 is int"));
    }

    @Test
    public void testMissingPartIsLeftOut() {
        assertEmbedding("[5, 0, 0, 0]", "input myText, input myImage | embed | attribute 'myTensor'", "hello", null);
        assertEmbedding("[5, 0, 0, 0]", "input myText, input myImage | embed | attribute 'myTensor'", "hello", new byte[0]);
        assertEmbedding("[0, 103, 0, 0]", "input myText, input myImage | embed | attribute 'myTensor'", null, image);
        assertEmbedding("[0, 103, 0, 0]", "input myText, input myImage | embed | attribute 'myTensor'", " ", image);
        assertEmbedding(null, "input myText, input myImage | embed | attribute 'myTensor'", null, null);
    }

    @Test
    public void testBinaryDataWhichIsNotImageIsRejected() {
        var e = assertThrows(InvalidInputException.class,
                             () -> embed(tester, "input myImage | embed | attribute 'myTensor'", null, new byte[] { 1, 2, 3 }));
        assertEquals("Binary data to embed must be a JPEG, PNG or GIF image", e.getCause().getMessage());
    }

    @Test
    public void testLanguageIsDetectedFromTextOnly() throws Exception {
        var detectedTexts = new ArrayList<String>();
        var linguistics = new SimpleLinguistics() {
            @Override
            public Detector getDetector() {
                Detector detector = super.getDetector();
                return new Detector() {
                    @Override
                    public Detection detect(byte[] input, int offset, int length, Hint hint) { return detector.detect(input, offset, length, hint); }
                    @Override
                    public Detection detect(ByteBuffer input, Hint hint) { return detector.detect(input, hint); }
                    @Override
                    public Detection detect(String input, Hint hint) {
                        detectedTexts.add(input);
                        return detector.detect(input, hint);
                    }
                };
            }
        };
        var context = new ScriptParserContext(linguistics, Map.of(), Map.of("emb1", new InputEmbedder()), Map.of())
                .setInputStream(new IndexingInput("input myText, input myImage | embed | attribute 'myTensor'"));

        assertEquals(embedding("[5, 103, 0, 0]"), embed(Expression.newInstance(context), tensorType.toString(), "hello", image));
        assertEquals(List.of("hello"), detectedTexts);
    }

    @Test
    public void testImageDoesNotDecideLanguage() {
        var languages = new ArrayList<Language>();
        var embedder = new InputEmbedder() {
            @Override
            public Tensor embed(Input input, Context context, TensorType tensorType) {
                languages.add(context.getLanguage());
                return super.embed(input, context, tensorType);
            }
        };
        var documentType = new DocumentType("myDocument");
        documentType.addField(new Field("myText", DataType.STRING));
        documentType.addField(new Field("myImage", DataType.RAW));
        var tensorField = new Field("myTensor", new TensorDataType(tensorType));
        documentType.addField(tensorField);
        documentType.addField(new Field("myTextTensor", new TensorDataType(tensorType)));
        var script = new EmbeddingScriptTester(Map.of("emb1", embedder))
                .expressionFrom("{ input myImage | embed | attribute myTensor; input myText | embed | attribute myTextTensor; }");
        script.setStatementOutput(documentType, tensorField);
        script.resolve(documentType);

        var document = new Document(documentType, "id:ns:myDocument::1");
        document.setFieldValue("myImage", new Raw(image));
        document.setFieldValue("myText", new StringFieldValue("こんにちは"));
        Expression.execute(script, document);

        // The image has no language, and the text after the image gets the language detected from that text
        assertEquals(List.of(Language.UNKNOWN, Language.JAPANESE), languages);
    }

    @Test
    public void testOnlyTextAloneIsBatched() {
        var embedder = new BatchingInputEmbedder();
        var batching = new EmbeddingScriptTester(Map.of("emb1", embedder));

        assertEquals(embedding("[5, 103, 0, 0]"),
                     embed(batching, "input myText, input myImage | embed | attribute 'myTensor'", "hello", image));
        assertEquals(1, embedder.inputWithImageCount);
        assertEquals(0, embedder.textBatchCount);

        assertEquals(embedding("[0, 103, 0, 0]"),
                     embed(batching, "input myImage | embed | attribute 'myTensor'", null, image));
        assertEquals(2, embedder.inputWithImageCount);
        assertEquals(0, embedder.textBatchCount);

        // A text whose image is missing is batched as a text
        assertEquals(embedding("[5, 0, 0, 0]"),
                     embed(batching, "input myText, input myImage | embed | attribute 'myTensor'", "hello", null));
        assertEquals(2, embedder.inputWithImageCount);
        assertEquals(1, embedder.textBatchCount);
    }

    @Test
    public void testPartialUpdateOnlyEmbedsWhenItHasAllParts() {
        var documentType = new DocumentType("myDocument");
        documentType.addField(new Field("myText", DataType.STRING));
        documentType.addField(new Field("myImage", DataType.RAW));
        var tensorField = new Field("myTensor", new TensorDataType(tensorType));
        documentType.addField(tensorField);
        var script = tester.expressionFrom("{ input myText | attribute myText; " +
                                           "input myText, input myImage | embed | attribute myTensor; }");
        script.setStatementOutput(documentType, tensorField);
        script.resolve(documentType);

        // An update of one part writes no embedding, since the stored document may have the other part
        var textUpdate = new DocumentUpdate(documentType, "id:ns:myDocument::1");
        textUpdate.addFieldUpdate(FieldUpdate.createAssign(documentType.getField("myText"), new StringFieldValue("hello")));
        assertEquals(Set.of("myText"), updatedFieldsOf(Expression.execute(script, textUpdate)));

        var imageUpdate = new DocumentUpdate(documentType, "id:ns:myDocument::1");
        imageUpdate.addFieldUpdate(FieldUpdate.createAssign(documentType.getField("myImage"), new Raw(image)));
        assertEquals(Set.of(), updatedFieldsOf(Expression.execute(script, imageUpdate)));

        var fullUpdate = new DocumentUpdate(documentType, "id:ns:myDocument::1");
        fullUpdate.addFieldUpdate(FieldUpdate.createAssign(documentType.getField("myText"), new StringFieldValue("hello")));
        fullUpdate.addFieldUpdate(FieldUpdate.createAssign(documentType.getField("myImage"), new Raw(image)));
        var output = Expression.execute(script, fullUpdate);
        assertEquals(Set.of("myText", "myTensor"), updatedFieldsOf(output));
        assertEquals(Tensor.from(tensorType, "[5, 103, 0, 0]"),
                     ((TensorFieldValue)output.getFieldUpdate("myTensor").getValueUpdate(0).getValue()).getTensor().get());
    }

    private static Set<String> updatedFieldsOf(DocumentUpdate update) {
        if (update == null) return Set.of();
        return update.fieldUpdates().stream().map(fieldUpdate -> fieldUpdate.getField().getName()).collect(Collectors.toSet());
    }

    @Test
    public void testTextEmbedderRejectsImage() {
        var textOnly = new EmbeddingScriptTester(Map.of("emb1", new EmbeddingScriptTester.MockIndexedEmbedder("myDocument.myTensor")));
        var e = assertThrows(IllegalArgumentException.class,
                             () -> embed(textOnly, "input myImage | embed emb1 | attribute 'myTensor'", null, image));
        assertEquals("Embedder 'emb1' accepts text, but is given image", e.getCause().getMessage());

        e = assertThrows(IllegalArgumentException.class,
                         () -> embed(textOnly, "input myImage | embed | attribute 'myTensor'", null, image));
        assertEquals("The embedder accepts text, but is given image", e.getCause().getMessage());

        e = assertThrows(IllegalArgumentException.class,
                         () -> embed(textOnly, "input myText, input myImage | embed emb1 | attribute 'myTensor'", "hello", image));
        assertEquals("Embedder 'emb1' accepts text, but is given text and image", e.getCause().getMessage());

        // The input type of the statement decides the rejection, also for a document without the image
        e = assertThrows(IllegalArgumentException.class,
                         () -> embed(textOnly, "input myText, input myImage | embed emb1 | attribute 'myTensor'", "hello", null));
        assertEquals("Embedder 'emb1' accepts text, but is given text and image", e.getCause().getMessage());
    }

    @Test
    public void testEmbedderWhichOnlyAcceptsImage() {
        var imageOnly = new EmbeddingScriptTester(Map.of("emb1", new InputEmbedder(Set.of(Set.of(IMAGE)))));
        assertEquals(embedding("[0, 103, 0, 0]"), embed(imageOnly, "input myImage | embed emb1 | attribute 'myTensor'", null, image));

        var e = assertThrows(IllegalArgumentException.class,
                             () -> embed(imageOnly, "input myText | embed emb1 | attribute 'myTensor'", "hello", null));
        assertEquals("Embedder 'emb1' accepts image, but is given text", e.getCause().getMessage());
    }

    @Test
    public void testEmbedderWhichAcceptsTextOrImageButNotBoth() {
        var textOrImage = new EmbeddingScriptTester(Map.of("emb1",
                                                           new InputEmbedder(Set.of(Set.of(TEXT), Set.of(IMAGE)))));
        assertEquals(embedding("[5, 0, 0, 0]"), embed(textOrImage, "input myText | embed emb1 | attribute 'myTensor'", "hello", null));
        assertEquals(embedding("[0, 103, 0, 0]"), embed(textOrImage, "input myImage | embed emb1 | attribute 'myTensor'", null, image));

        var e = assertThrows(IllegalArgumentException.class,
                             () -> embed(textOrImage, "input myText, input myImage | embed emb1 | attribute 'myTensor'", "hello", image));
        assertEquals("Embedder 'emb1' accepts image or text, but is given text and image", e.getCause().getMessage());
    }

    @Test
    public void testEmbedderWhichOnlyAcceptsTextWithImage() {
        var textWithImage = new EmbeddingScriptTester(Map.of("emb1", new InputEmbedder(Set.of(Set.of(TEXT, IMAGE)))));
        String statement = "input myText, input myImage | embed emb1 | attribute 'myTensor'";
        assertEquals(embedding("[5, 103, 0, 0]"), embed(textWithImage, statement, "hello", image));

        // The statement gives a text with an image, but a document which lacks the image only gives a text
        var e = assertThrows(InvalidInputException.class, () -> embed(textWithImage, statement, "hello", null));
        assertEquals("Embedder 'emb1' accepts text and image, but is given text", e.getCause().getMessage());
    }

    @Test
    public void testOnlyEmbedAcceptsParts() {
        var e = assertThrows(VerificationException.class,
                             () -> embed(tester, "input myText, input myImage | lowercase | embed | attribute 'myTensor'", "hello", image));
        assertTrue(e.getMessage(), e.getMessage().contains("A comma separates parts, and parts must be directly followed by '| embed'"));

        e = assertThrows(VerificationException.class,
                         () -> embed(tester, "input myText, input myImage | attribute 'myTensor'", "hello", image));
        assertTrue(e.getMessage(), e.getMessage().contains("A comma separates parts, and parts must be directly followed by '| embed'"));
    }

    @Test
    public void testPartsWhichEndStatementAreRejected() {
        // A comma in place of a pipe makes a multipart input which ends the statement
        var e = assertThrows(VerificationException.class,
                             () -> embed(tester, "input myText | embed | attribute 'myTensor', summary 'myTensor'", "hello", image));
        assertTrue(e.getMessage(), e.getMessage().contains("A comma separates parts, and parts must be directly followed by '| embed'"));
    }

    @Test
    public void testPartsMustStandDirectlyBeforeEmbed() {
        // Parentheses start a nested statement, which ends with the multipart input
        var e = assertThrows(VerificationException.class,
                             () -> embed(tester, "(input myText, input myImage) | embed | attribute 'myTensor'", "hello", image));
        assertTrue(e.getMessage(), e.getMessage().contains("parts must be directly followed by '| embed'"));

        // A choice follows the multipart input, not embed
        e = assertThrows(VerificationException.class,
                         () -> embed(tester, "input myText, input myImage | embed || embed | attribute 'myTensor'", "hello", image));
        assertTrue(e.getMessage(), e.getMessage().contains("parts must be directly followed by '| embed'"));

        // The statement in for_each ends with a multipart input
        var expression = tester.expressionFrom("input myTexts | for_each { _, input myImage } | to_string | embed | " +
                                               "attribute 'myTensor'");
        var adapter = new SimpleTestAdapter();
        adapter.createField(new Field("myTexts", new ArrayDataType(DataType.STRING)));
        adapter.createField(new Field("myImage", DataType.RAW));
        var tensorField = new Field("myTensor", new TensorDataType(tensorType));
        adapter.createField(tensorField);
        expression.setStatementOutput(new DocumentType("myDocument"), tensorField);
        e = assertThrows(VerificationException.class, () -> expression.resolve(new TypeContext(adapter)));
        assertTrue(e.getMessage(), e.getMessage().contains("parts must be directly followed by '| embed'"));
    }

    @Test
    public void testPartsRequireSingleEmbeddingTarget() {
        var e = assertThrows(VerificationException.class,
                             () -> embed(tester, "input myText, input myImage | embed emb1 chunk | attribute 'myTensor'",
                                         "tensor(chunk{},token{},d[4])", "hello", image));
        assertTrue(e.getMessage(),
                   e.getMessage().contains("Input is a string and a raw, so output must be a rank 1 tensor"));
    }

    @Test
    public void testToString() {
        assertEquals("input myText . \" \" . input myText, input myImage | embed emb1 | attribute myTensor",
                     tester.expressionFrom("input myText . ' ' . input myText, input myImage | embed emb1 | attribute myTensor").toString());
        assertEquals("(input myText | lowercase), input myImage | embed emb1 | attribute myTensor",
                     tester.expressionFrom("(input myText | lowercase), input myImage | embed emb1 | attribute myTensor").toString());
    }

    private static Optional<Tensor> embedding(String tensor) {
        return Optional.of(Tensor.from(tensorType, tensor));
    }

    private void assertEmbedding(String expected, String statement, String text, byte[] image) {
        assertEquals(Optional.ofNullable(expected).map(e -> Tensor.from(tensorType, e)),
                     embed(tester, statement, text, image));
    }

    private static Optional<Tensor> embed(EmbeddingScriptTester tester, String statement, String text, byte[] image) {
        return embed(tester, statement, tensorType.toString(), text, image);
    }

    private static Optional<Tensor> embed(EmbeddingScriptTester tester, String statement, String targetType,
                                          String text, byte[] image) {
        return embed(tester.expressionFrom(statement), targetType, text, image);
    }

    /** Embeds a complete document, as a put does, and returns the embedding written to the document, if any. */
    private static Optional<Tensor> embed(Expression expression, String targetType, String text, byte[] image) {
        var documentType = new DocumentType("myDocument");
        documentType.addField(new Field("myText", DataType.STRING));
        documentType.addField(new Field("myImage", DataType.RAW));
        var tensorField = new Field("myTensor", new TensorDataType(TensorType.fromSpec(targetType)));
        documentType.addField(tensorField);
        var document = new Document(documentType, "id:ns:myDocument::1");
        if (text != null)
            document.setFieldValue("myText", new StringFieldValue(text));
        if (image != null)
            document.setFieldValue("myImage", new Raw(image));
        expression.setStatementOutput(documentType, tensorField);
        var output = Expression.execute(expression, document);
        return Optional.ofNullable((TensorFieldValue)output.getFieldValue("myTensor")).flatMap(TensorFieldValue::getTensor);
    }

    /** An embedder with dynamic batching, which counts its batches of texts and its inputs with an image. */
    private static class BatchingInputEmbedder extends InputEmbedder {

        int textBatchCount = 0;
        int inputWithImageCount = 0;

        @Override
        public Batching batchingConfig() { return Batching.of(8, Duration.ofMillis(100)); }

        @Override
        public List<Tensor> embed(List<String> texts, Context context, TensorType tensorType) {
            textBatchCount++;
            return super.embed(texts, context, tensorType);
        }

        @Override
        public Tensor embed(Input input, Context context, TensorType tensorType) {
            if (input.image().isPresent()) inputWithImageCount++;
            return super.embed(input, context, tensorType);
        }

    }

    /**
     * An embedder which returns the length of the text in the first cell,
     * and 100 plus the number of bytes of the image in the second cell.
     * A part which the input does not have gives 0.
     */
    private static class InputEmbedder implements Embedder {

        private final Set<Set<Modality>> supportedModalities;

        InputEmbedder() { this(Set.of(Set.of(TEXT), Set.of(IMAGE), Set.of(TEXT, IMAGE))); }

        InputEmbedder(Set<Set<Modality>> supportedModalities) {
            this.supportedModalities = supportedModalities;
        }

        @Override
        public Set<Set<Modality>> supportedModalities() { return supportedModalities; }

        @Override
        public List<Integer> embed(String text, Context context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Tensor embed(String text, Context context, TensorType tensorType) {
            return embed(Input.of(text), context, tensorType);
        }

        @Override
        public Tensor embed(Input input, Context context, TensorType tensorType) {
            assertEquals("myDocument.myTensor", context.getDestination());
            int text = input.text().map(String::length).orElse(0);
            int image = input.image().map(i -> 100 + i.data().remaining()).orElse(0);
            return Tensor.from(tensorType, "[" + text + ", " + image + ", 0, 0]");
        }

    }

}
