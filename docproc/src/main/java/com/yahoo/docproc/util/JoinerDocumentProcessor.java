// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.util;

import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.config.docproc.SplitterJoinerDocumentProcessorConfig;
import com.yahoo.docproc.DocumentProcessor;
import com.yahoo.docproc.Processing;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentTypeManagerConfigurer;
import com.yahoo.document.datatypes.Array;
import java.util.logging.Level;

import java.util.logging.Logger;

import static com.yahoo.docproc.util.SplitterDocumentProcessor.validate;
import static com.yahoo.docproc.util.SplitterDocumentProcessor.doProcessOuterDocument;

/**
 * @author Einar M R Rosenvinge
 */
public class JoinerDocumentProcessor extends DocumentProcessor {

    private static Logger log = Logger.getLogger(JoinerDocumentProcessor.class.getName());
    private String documentTypeName;
    private String arrayFieldName;
    private String contextFieldName;
    private DocumentTypeManager manager;

    public JoinerDocumentProcessor(SplitterJoinerDocumentProcessorConfig cfg, DocumentmanagerConfig documentmanagerConfig) {
        super();
        this.documentTypeName = cfg.documentTypeName();
        this.arrayFieldName = cfg.arrayFieldName();
        this.contextFieldName = cfg.contextFieldName();
        manager = DocumentTypeManagerConfigurer.configureNewManager(documentmanagerConfig);
        validate(manager, documentTypeName, arrayFieldName);
    }

    @Override
    public Progress process(Processing processing) {
        if ( ! doProcessOuterDocument(processing.getVariable(contextFieldName), documentTypeName)) {
            return Progress.DONE;
        }

        DocumentPut outerDoc = (DocumentPut)processing.getVariable(contextFieldName);

        @SuppressWarnings("unchecked")
        Array<Document> innerDocuments = (Array<Document>) outerDoc.getDocument().getFieldValue(arrayFieldName);

        if (innerDocuments == null) {
            @SuppressWarnings("unchecked")
            Array<Document> empty = (Array<Document>) outerDoc.getDocument().getDataType().getField(arrayFieldName).getDataType().createFieldValue();
            innerDocuments = empty;
        }

        for (DocumentOperation op : processing.getDocumentOperations()) {
            if (op instanceof DocumentPut) {
                innerDocuments.add(((DocumentPut)op).getDocument());
            } else {
                log.log(Level.FINE, () -> "Skipping: " + op);
            }
        }
        processing.getDocumentOperations().clear();
        processing.getDocumentOperations().add(outerDoc);
        processing.removeVariable(contextFieldName);
        return Progress.DONE;
    }

}
