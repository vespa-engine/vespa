// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docprocs.indexing;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.update.FieldUpdate;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
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

}
