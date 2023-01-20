// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docprocs.indexing;

import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.docproc.Processing;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.update.AssignValueUpdate;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.document.update.ValueUpdate;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import org.junit.Test;

import java.util.List;

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

    private static final String CONFIG_ID = "dir:src/test/cfg";

    private final IndexingProcessor indexer = newProcessor(CONFIG_ID);

    @Test
    public void requireThatIndexerProcessesDocuments() {
        Document input = new Document(indexer.getDocumentTypeManager().getDocumentType("music"), "id:ns:music::");
        input.setFieldValue("artist", new StringFieldValue("69"));
        DocumentOperation op = process(new DocumentPut(input));
        assertTrue(op instanceof DocumentPut);

        Document output = ((DocumentPut)op).getDocument();
        assertEquals(new StringFieldValue("69"), output.getFieldValue("title"));
        assertEquals("music", output.getDataType().getName());
    }

    @Test
    public void requireThatIndexerForwardsDocumentsOfUnknownType() {
        Document input = new Document(new DocumentType("unknown"), "id:ns:unknown::");
        DocumentOperation output = process(new DocumentPut(input));
        assertTrue(output instanceof DocumentPut);
        assertSame(input, ((DocumentPut)output).getDocument());
    }

    @Test
    public void testFieldUpdates() {
        // 'artist' is assigned to 'title' and vice versa
        // 'combined' gets the value of both
        // 'combinedWithFallback' falls back to an empty string if an input is missing

        {   // Both inputs are set
            DocumentType inputType = indexer.getDocumentTypeManager().getDocumentType("music");
            DocumentUpdate input = new DocumentUpdate(inputType, "id:ns:music::");
            input.addFieldUpdate(FieldUpdate.createAssign(inputType.getField("artist"), new StringFieldValue("artist1")));
            input.addFieldUpdate(FieldUpdate.createAssign(inputType.getField("title"), new StringFieldValue("title1")));

            DocumentUpdate output = (DocumentUpdate)process(input);
            assertEquals(4, output.fieldUpdates().size());
            assertAssignment("artist", "title1", output);
            assertAssignment("title", "artist1", output);
            assertAssignment("combined", "artist1 title1", output);
            assertAssignment("combinedWithFallback", "artist1 title1", output);
        }

        {   // Just artist is set
            DocumentType inputType = indexer.getDocumentTypeManager().getDocumentType("music");
            DocumentUpdate input = new DocumentUpdate(inputType, "id:ns:music::");
            input.addFieldUpdate(FieldUpdate.createAssign(inputType.getField("artist"), new StringFieldValue("artist1")));

            DocumentUpdate output = (DocumentUpdate)process(input);
            assertEquals(2, output.fieldUpdates().size());
            assertAssignment("title", "artist1", output);
            assertAssignment("combinedWithFallback", "artist1 ", output);
        }

        {   // Just title is set
            DocumentType inputType = indexer.getDocumentTypeManager().getDocumentType("music");
            DocumentUpdate input = new DocumentUpdate(inputType, "id:ns:music::");
            input.addFieldUpdate(FieldUpdate.createAssign(inputType.getField("title"), new StringFieldValue("title1")));

            DocumentUpdate output = (DocumentUpdate)process(input);
            assertEquals(2, output.fieldUpdates().size());
            assertAssignment("artist", "title1", output);
            assertAssignment("combinedWithFallback", " title1", output);
        }

        {   // Neither title nor artist is set: Should not update embeddings even though it has fallbacks for all
            DocumentType inputType = indexer.getDocumentTypeManager().getDocumentType("music");
            DocumentUpdate input = new DocumentUpdate(inputType, "id:ns:music::");
            input.addFieldUpdate(FieldUpdate.createAssign(inputType.getField("isbn"), new StringFieldValue("isbn1")));

            DocumentUpdate output = (DocumentUpdate)process(input);
            assertEquals(2, output.fieldUpdates().size());
            assertAssignment("isbn", "isbn1", output);
            assertAssignment("song", "isbn1", output);
        }

        {   // None is set: Should not update anything
            DocumentType inputType = indexer.getDocumentTypeManager().getDocumentType("music");
            DocumentUpdate input = new DocumentUpdate(inputType, "id:ns:music::");

            DocumentUpdate output = (DocumentUpdate)process(input);
            assertNull(output);
        }
    }

    @Test
    public void requireThatIndexerForwardsUpdatesOfUnknownType() {
        DocumentUpdate input = new DocumentUpdate(new DocumentType("unknown"), "id:ns:music::");
        DocumentOperation output = process(input);
        assertSame(input, output);
    }

    private void assertAssignment(String fieldName, String value, DocumentUpdate output) {
        FieldUpdate update = output.getFieldUpdate(fieldName);
        assertNotNull("Update of '" + fieldName + "' exists", update);
        assertEquals(fieldName, update.getField().getName());
        assertEquals(1, update.getValueUpdates().size());
        ValueUpdate<?> combinedAssignment = update.getValueUpdate(0);
        assertTrue(combinedAssignment instanceof AssignValueUpdate);
        assertEquals(new StringFieldValue(value), combinedAssignment.getValue());
    }

    private DocumentOperation process(DocumentOperation input) {
        Processing proc = new Processing();
        proc.getDocumentOperations().add(input);
        indexer.process(proc);

        List<DocumentOperation> operations = proc.getDocumentOperations();
        if (operations.isEmpty()) return null;
        assertEquals(1, operations.size());
        return operations.get(0);
    }

    @SuppressWarnings("deprecation")
    private static IndexingProcessor newProcessor(String configId) {
        return new IndexingProcessor(new DocumentTypeManager(ConfigGetter.getConfig(DocumentmanagerConfig.class, configId)),
                                     ConfigGetter.getConfig(IlscriptsConfig.class, configId),
                                     new SimpleLinguistics(),
                                     new ComponentRegistry<>());
    }
}
