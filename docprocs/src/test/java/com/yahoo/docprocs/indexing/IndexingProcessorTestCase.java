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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class IndexingProcessorTestCase {

    private static final String CONFIG_ID = "dir:src/test/cfg";
    private IndexingProcessor indexer = newProcessor(CONFIG_ID);

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
    public void requireThatIndexerProcessesUpdates() {
        DocumentType inputType = indexer.getDocumentTypeManager().getDocumentType("music");
        DocumentUpdate input = new DocumentUpdate(inputType, "id:ns:music::");
        input.addFieldUpdate(FieldUpdate.createAssign(inputType.getField("isbn"), new StringFieldValue("isbnmarker")));
        input.addFieldUpdate(FieldUpdate.createAssign(inputType.getField("artist"), new StringFieldValue("69")));
        DocumentOperation output = process(input);

        assertTrue(output instanceof DocumentUpdate);
        DocumentUpdate docUpdate = (DocumentUpdate) output;

        assertEquals(3, docUpdate.fieldUpdates().size());
        {
            FieldUpdate fieldUpdate = docUpdate.getFieldUpdate("song");
            assertEquals("song", fieldUpdate.getField().getName());
            assertEquals(1, fieldUpdate.getValueUpdates().size());
            ValueUpdate<?> valueUpdate = fieldUpdate.getValueUpdate(0);
            assertTrue(valueUpdate instanceof AssignValueUpdate);
            assertEquals(new StringFieldValue("isbnmarker"), valueUpdate.getValue());
            fieldUpdate = docUpdate.getFieldUpdate("title");
            assertEquals("title", fieldUpdate.getField().getName());
            assertEquals(1, fieldUpdate.getValueUpdates().size());
            valueUpdate = fieldUpdate.getValueUpdate(0);
            assertTrue(valueUpdate instanceof AssignValueUpdate);
            assertEquals(new StringFieldValue("69"), valueUpdate.getValue());
        }

        {
            FieldUpdate fieldUpdate = docUpdate.getFieldUpdate("title");
            ValueUpdate<?> valueUpdate = fieldUpdate.getValueUpdate(0);
            assertEquals("title", fieldUpdate.getField().getName());
            assertTrue(valueUpdate instanceof AssignValueUpdate);
            assertEquals(new StringFieldValue("69"), valueUpdate.getValue());
        }
        {
            FieldUpdate fieldUpdate = docUpdate.getFieldUpdate("isbn");
            ValueUpdate<?> valueUpdate = fieldUpdate.getValueUpdate(0);
            assertEquals("isbn", fieldUpdate.getField().getName());
            assertTrue(valueUpdate instanceof AssignValueUpdate);
            assertEquals(new StringFieldValue("isbnmarker"), valueUpdate.getValue());
        }

    }

    @Test
    public void requireThatEmptyDocumentUpdateOutputDoesNotThrow() {
        DocumentType inputType = indexer.getDocumentTypeManager().getDocumentType("music");
        DocumentUpdate input = new DocumentUpdate(inputType, "id:ns:music::");
        Processing proc = new Processing();
        proc.getDocumentOperations().add(input);
        indexer.process(proc);
        assertEquals(0, proc.getDocumentOperations().size());
    }

    @Test
    public void requireThatIndexerForwardsUpdatesOfUnknownType() {
        DocumentUpdate input = new DocumentUpdate(new DocumentType("unknown"), "id:ns:music::");
        DocumentOperation output = process(input);
        assertSame(input, output);
    }

    private DocumentOperation process(DocumentOperation input) {
        Processing proc = new Processing();
        proc.getDocumentOperations().add(input);
        indexer.process(proc);

        List<DocumentOperation> lst = proc.getDocumentOperations();
        assertEquals(1, lst.size());
        return lst.get(0);
    }

    @SuppressWarnings("deprecation")
    private static IndexingProcessor newProcessor(String configId) {
        return new IndexingProcessor(new DocumentTypeManager(ConfigGetter.getConfig(DocumentmanagerConfig.class, configId)),
                                     ConfigGetter.getConfig(IlscriptsConfig.class, configId),
                                     new SimpleLinguistics(),
                                     new ComponentRegistry<Embedder>());
    }
}
