// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docprocs.indexing;

import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.docproc.Processing;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.update.AssignValueUpdate;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.document.update.ValueUpdate;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class IndexingProcessorTester {

    private final IndexingProcessor indexer;

    public IndexingProcessorTester() {
        indexer = newProcessor("raw:");
    }

    public IndexingProcessorTester(String configDir) {
        indexer = newProcessor("dir:" + configDir);
    }

    public DocumentType getDocumentType(String name) {
        return indexer.getDocumentTypeManager().getDocumentType(name);
    }

    public void assertAssignment(String fieldName, String value, DocumentUpdate output) {
        FieldUpdate update = output.getFieldUpdate(fieldName);
        assertNotNull("Update of '" + fieldName + "' exists", update);
        assertEquals(fieldName, update.getField().getName());
        assertEquals(1, update.getValueUpdates().size());
        ValueUpdate<?> combinedAssignment = update.getValueUpdate(0);
        assertTrue(combinedAssignment instanceof AssignValueUpdate);
        assertEquals(new StringFieldValue(value), combinedAssignment.getValue());
    }

    public DocumentOperation process(DocumentOperation input) {
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
                                     new ComponentRegistry<>(),
                                     new ComponentRegistry<>());
    }

}
