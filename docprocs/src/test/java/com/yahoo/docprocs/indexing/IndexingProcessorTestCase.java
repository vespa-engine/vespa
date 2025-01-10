// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docprocs.indexing;

import com.yahoo.component.AbstractComponent;
import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.update.AssignValueUpdate;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.TextGenerator;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.Tensors;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class IndexingProcessorTestCase {

    @Test
    public void requireThatIndexerForwardsDocumentsOfUnknownType() {
        var tester = new IndexingProcessorTester();
        Document input = new Document(new DocumentType("unknown"), "id:ns:unknown::");
        DocumentOperation output = tester.process(new DocumentPut(input));
        assertTrue(output instanceof DocumentPut);
        assertSame(input, ((DocumentPut)output).getDocument());
    }

    @Test
    public void testPut() {
        IndexingProcessorTester tester = new IndexingProcessorTester("src/test/cfg");
        // 'combined' gets the value of both
        // 'combinedWithFallback' falls back to an empty string if an input is missing

        {   // Both artist and title are set
            DocumentType inputType = tester.getDocumentType("music");
            DocumentPut input = new DocumentPut(inputType, "id:ns:music::");
            input.getDocument().setFieldValue(inputType.getField("artist"), new StringFieldValue("artist1"));
            input.getDocument().setFieldValue(inputType.getField("title"), new StringFieldValue("title1"));

            Document output = ((DocumentPut)tester.process(input)).getDocument();
            assertEquals("artist1", output.getFieldValue("artist").getWrappedValue());
            assertEquals("title1", output.getFieldValue("title").getWrappedValue());
            assertNull(output.getFieldValue("song"));
            assertEquals("artist1 title1", output.getFieldValue("combined").getWrappedValue());
            assertEquals("artist1 title1", output.getFieldValue("combinedWithFallback").getWrappedValue());
        }

        {   // Just artist is set
            DocumentType inputType = tester.getDocumentType("music");
            DocumentPut input = new DocumentPut(inputType, "id:ns:music::");
            input.getDocument().setFieldValue(inputType.getField("artist"), new StringFieldValue("artist1"));

            Document output = ((DocumentPut)tester.process(input)).getDocument();
            assertEquals("artist1", output.getFieldValue("artist").getWrappedValue());
            assertNull(output.getFieldValue("title"));
            assertNull(output.getFieldValue("song"));
            assertNull(output.getFieldValue("combined"));
            assertEquals("artist1 ", output.getFieldValue("combinedWithFallback").getWrappedValue());
        }

        {   // Just title is set
            DocumentType inputType = tester.getDocumentType("music");
            DocumentPut input = new DocumentPut(inputType, "id:ns:music::");
            input.getDocument().setFieldValue(inputType.getField("title"), new StringFieldValue("title1"));

            Document output = ((DocumentPut)tester.process(input)).getDocument();
            assertEquals("title1", output.getFieldValue("title").getWrappedValue());
            assertNull(output.getFieldValue("artist"));
            assertNull(output.getFieldValue("song"));
            assertNull(output.getFieldValue("combined"));
            assertEquals(" title1", output.getFieldValue("combinedWithFallback").getWrappedValue());
        }

        {   // Neither title nor artist is set
            DocumentType inputType = tester.getDocumentType("music");
            DocumentPut input = new DocumentPut(inputType, "id:ns:music::");
            input.getDocument().setFieldValue(inputType.getField("song"), new StringFieldValue("song1"));

            Document output = ((DocumentPut)tester.process(input)).getDocument();
            assertNull(output.getFieldValue("artist"));
            assertNull(output.getFieldValue("title"));
            assertEquals("song1", output.getFieldValue("song").getWrappedValue());
            assertNull(output.getFieldValue("combined"));
            assertEquals(" ", output.getFieldValue("combinedWithFallback").getWrappedValue());
        }

        {   // None is set
            DocumentType inputType = tester.getDocumentType("music");
            DocumentPut input = new DocumentPut(inputType, "id:ns:music::");

            Document output = ((DocumentPut)tester.process(input)).getDocument();
            assertNull(output.getFieldValue("artist"));
            assertNull(output.getFieldValue("title"));
            assertNull(output.getFieldValue("song"));
            assertNull(output.getFieldValue("combined"));
            assertEquals(" ", output.getFieldValue("combinedWithFallback").getWrappedValue());
        }
    }

    @Test
    public void testPutPosition() {
        // Config of the following schema, derived Nov 2024, by SchemaTestCase.testDerivingPosition in the config-model
        //
        //                schema place {
        //
        //                    document place {
        //
        //                        field location type position {
        //                            indexing: attribute
        //                        }
        //                    }
        //                }
        IndexingProcessorTester tester = new IndexingProcessorTester("src/test/cfg3");

        DocumentType inputType = tester.getDocumentType("place");
        DocumentPut input = new DocumentPut(inputType, "id:ns:place::");
        input.getDocument().setFieldValue(inputType.getField("location"), PositionDataType.fromString("13;17"));

        Document output = ((DocumentPut)tester.process(input)).getDocument();
        assertEquals(595L, output.getFieldValue("location_zcurve").getWrappedValue());
    }

    @Test
    public void testPutLongHash() {
        // Config of the following schema, derived Nov 2024, by SchemaTestCase.testDeriving in the config-model
        //
        //                schema page {
        //
        //                    field domain_hash type long {
        //                        indexing: input domain | hash | attribute
        //                    }
        //
        //                    document page {
        //
        //                        field domain type string {
        //                            indexing: index | summary
        //                            match: word
        //                            rank: filter
        //                        }
        //                    }
        //                }
        IndexingProcessorTester tester = new IndexingProcessorTester("src/test/cfg2");

        DocumentType inputType = tester.getDocumentType("page");
        DocumentPut input = new DocumentPut(inputType, "id:ns:page::");
        input.getDocument().setFieldValue(inputType.getField("domain"), new StringFieldValue("domain1"));

        Document output = ((DocumentPut)tester.process(input)).getDocument();
        assertEquals("domain1", output.getFieldValue("domain").getWrappedValue());
        assertEquals(1386505442371493468L, output.getFieldValue("domain_hash").getWrappedValue());
    }

    @Test
    public void testUpdate() {
        IndexingProcessorTester tester = new IndexingProcessorTester("src/test/cfg");
        // 'combined' gets the value of artist and title
        // 'combinedWithFallback' falls back to an empty string if an input is missing

        {   // Both artist and title are set
            DocumentType inputType = tester.getDocumentType("music");
            DocumentUpdate input = new DocumentUpdate(inputType, "id:ns:music::");
            input.addFieldUpdate(FieldUpdate.createAssign(inputType.getField("artist"), new StringFieldValue("artist1")));
            input.addFieldUpdate(FieldUpdate.createAssign(inputType.getField("title"), new StringFieldValue("title1")));

            DocumentUpdate output = (DocumentUpdate)tester.process(input);
            assertEquals(4, output.fieldUpdates().size());
            tester.assertAssignment("artist", "artist1", output);
            tester.assertAssignment("title", "title1", output);
            tester.assertAssignment("combined", "artist1 title1", output);
            tester.assertAssignment("combinedWithFallback", "artist1 title1", output);
        }

        {   // Just artist is set
            DocumentType inputType = tester.getDocumentType("music");
            DocumentUpdate input = new DocumentUpdate(inputType, "id:ns:music::");
            input.addFieldUpdate(FieldUpdate.createAssign(inputType.getField("artist"), new StringFieldValue("artist1")));

            DocumentUpdate output = (DocumentUpdate)tester.process(input);
            assertEquals(2, output.fieldUpdates().size());
            tester.assertAssignment("artist", "artist1", output);
            tester.assertAssignment("combinedWithFallback", "artist1 ", output);
        }

        {   // Just title is set
            DocumentType inputType = tester.getDocumentType("music");
            DocumentUpdate input = new DocumentUpdate(inputType, "id:ns:music::");
            input.addFieldUpdate(FieldUpdate.createAssign(inputType.getField("title"), new StringFieldValue("title1")));

            DocumentUpdate output = (DocumentUpdate)tester.process(input);
            assertEquals(2, output.fieldUpdates().size());
            tester.assertAssignment("title", "title1", output);
            tester.assertAssignment("combinedWithFallback", " title1", output);
        }

        {   // Neither title nor artist is set: Should not update embeddings even though it has fallbacks for all
            DocumentType inputType = tester.getDocumentType("music");
            DocumentUpdate input = new DocumentUpdate(inputType, "id:ns:music::");
            input.addFieldUpdate(FieldUpdate.createAssign(inputType.getField("song"), new StringFieldValue("song1")));

            DocumentUpdate output = (DocumentUpdate)tester.process(input);
            assertEquals(1, output.fieldUpdates().size());
            tester.assertAssignment("song", "song1", output);
        }

        {   // None is set: Should not update anything
            DocumentType inputType = tester.getDocumentType("music");
            DocumentUpdate input = new DocumentUpdate(inputType, "id:ns:music::");

            DocumentUpdate output = (DocumentUpdate)tester.process(input);
            assertNull(output);
        }
    }

    @Test
    public void requireThatIndexerForwardsUpdatesOfUnknownType() {
        var tester = new IndexingProcessorTester();
        DocumentUpdate input = new DocumentUpdate(new DocumentType("unknown"), "id:ns:music::");
        DocumentOperation output = tester.process(input);
        assertSame(input, output);
    }

    @Test
    public void testEmbedBinarizeAndPack() {
        var documentTypes = new DocumentTypeManager();
        var test = new DocumentType("test");
        test.addField("myText", DataType.STRING);
        test.addField("embedding", new TensorDataType(TensorType.fromSpec("tensor<int8>(x[16])")));
        documentTypes.register(test);

        IlscriptsConfig.Builder config = new IlscriptsConfig.Builder();
        config.ilscript(new IlscriptsConfig.Ilscript.Builder().doctype("test")
                                                              .content("input myText | embed | binarize | pack_bits | attribute embedding")
                                                              .docfield("myText"));
        var scripts = new ScriptManager(documentTypes, new IlscriptsConfig(config), null, 
                Map.of("test", new TestEmbedder()), TextGenerator.throwsOnUse.asMap());
        
        assertNotNull(scripts.getScript(documentTypes.getDocumentType("test")));

        var tester = new IndexingProcessorTester(documentTypes, scripts);
        DocumentUpdate input = new DocumentUpdate(test, "id:ns:test::");
        input.addFieldUpdate(FieldUpdate.createAssign(test.getField("myText"), new StringFieldValue("my text")));
        DocumentUpdate output = (DocumentUpdate)tester.process(input);
        FieldUpdate embeddingUpdate = output.getFieldUpdate("embedding");
        AssignValueUpdate valueUpdate = (AssignValueUpdate)embeddingUpdate.getValueUpdate(0);
        assertEquals(Tensor.from("tensor<int8>(x[16]):[-110, 73, 36, -110, 73, 36, -110, 73, 36, -110, 73, 36, -110, 73, 36, -110]"),
                                 valueUpdate.getValue().getWrappedValue());
    }

    /** An ebedder which also does its own quantization, similar to HuggingFaceEmbedder. */
    static class TestEmbedder extends AbstractComponent implements Embedder {

        @Override
        public List<Integer> embed(String s, Context context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Tensor embed(String text, Context context, TensorType tensorType) {
            if (tensorType.dimensions().size() != 1)
                throw new IllegalArgumentException("Error in embedding to type '" + tensorType + "': should only have one dimension.");
            if (!tensorType.dimensions().get(0).isIndexed())
                throw new IllegalArgumentException("Error in embedding to type '" + tensorType + "': dimension should be indexed.");
            boolean binarize = tensorType.valueType() == TensorType.Value.INT8;
            long size = tensorType.dimensions().get(0).size().get();
            if (binarize)
                size = size * 8;
            var embeddedType = new TensorType.Builder().indexed(tensorType.dimensions().get(0).name(), size).build();
            var resultBuilder = Tensor.Builder.of(embeddedType);
            for (int i = 0; i < size; i++) {
                int v = ((i % 3) == 0) ? 1 : 0;
                resultBuilder.cell(v, i);
            }
            var result = resultBuilder.build();
            if (binarize)
                result = Tensors.packBits(result);
             return result;
        }

    }

}
