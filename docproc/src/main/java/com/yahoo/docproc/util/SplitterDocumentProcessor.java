// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.util;

import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.config.docproc.SplitterJoinerDocumentProcessorConfig;
import com.yahoo.docproc.DocumentProcessor;
import com.yahoo.docproc.Processing;
import com.yahoo.document.ArrayDataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentTypeManagerConfigurer;
import com.yahoo.document.datatypes.Array;
import java.util.logging.Level;

import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author Einar M R Rosenvinge
 */
public class SplitterDocumentProcessor extends DocumentProcessor {

    private static Logger log = Logger.getLogger(SplitterDocumentProcessor.class.getName());
    private String documentTypeName;
    private String arrayFieldName;
    private String contextFieldName;
    DocumentTypeManager manager;

    public SplitterDocumentProcessor(SplitterJoinerDocumentProcessorConfig cfg, DocumentmanagerConfig documentmanagerConfig) {
        super();
        this.documentTypeName = cfg.documentTypeName();
        this.arrayFieldName = cfg.arrayFieldName();
        this.contextFieldName = cfg.contextFieldName();
        this.manager = DocumentTypeManagerConfigurer.configureNewManager(documentmanagerConfig);
        validate(manager, documentTypeName, arrayFieldName);
    }

    @Override
    public Progress process(Processing processing) {
        if (processing.getDocumentOperations().size() != 1) {
            //we were given more than one document, return
            log.log(Level.FINE, () -> "More than one document given, returning. (Was given "
                                          + processing.getDocumentOperations().size() + " documents).");
            return Progress.DONE;
        }

        if (!doProcessOuterDocument(processing.getDocumentOperations().get(0), documentTypeName)) {
            return Progress.DONE;
        }

        Document outerDoc = ((DocumentPut)processing.getDocumentOperations().get(0)).getDocument();;

        @SuppressWarnings("unchecked")
        Array<Document> innerDocuments = (Array<Document>) outerDoc.getFieldValue(arrayFieldName);
        if (innerDocuments == null) {
            //the document does not have the field, return
            log.log(Level.FINE, () -> "The given Document does not have a field value for field "
                                          + arrayFieldName + ", returning. (Was given " + outerDoc + ").");
            return Progress.DONE;
        }

        if (innerDocuments.size() == 0) {
            //the array is empty, return
            log.log(Level.FINE, () -> "The given Document does not have any elements in array field "
                                          + arrayFieldName + ", returning. (Was given " + outerDoc + ").");
            return Progress.DONE;
        }

        split(processing, innerDocuments);
        return Progress.DONE;
    }

    private void split(Processing processing, Array<Document> innerDocuments) {
        processing.setVariable(contextFieldName, processing.getDocumentOperations().get(0));
        processing.getDocumentOperations().clear();
        processing.getDocumentOperations().addAll(innerDocuments.stream()
                .map(DocumentPut::new)
                .collect(Collectors.toList()));

        innerDocuments.clear();
    }


    static void validate(DocumentTypeManager manager, String documentTypeName, String arrayFieldName) {
        DocumentType docType = manager.getDocumentType(documentTypeName);

        if (docType == null) {
            //the document type does not exist, return
            throw new IllegalStateException("The document type " + documentTypeName + " is not deployed.");
        }

        if (docType.getField(arrayFieldName) == null) {
            //the document type does not have the field, return
            throw new IllegalStateException("The document type " + documentTypeName
                                            + " does not have a field named " + arrayFieldName + ".");
        }

        if (!(docType.getField(arrayFieldName).getDataType() instanceof ArrayDataType)) {
            //the data type of the field is wrong, return
            throw new IllegalStateException("The data type of the field named "
                                            + arrayFieldName + " in document type " + documentTypeName
                                            + " is not an array type");
        }

        ArrayDataType fieldDataType = (ArrayDataType) docType.getField(arrayFieldName).getDataType();

        if (!(fieldDataType.getNestedType() instanceof DocumentType)) {
            //the subtype of tye array data type of the field is wrong, return
            throw new IllegalStateException("The data type of the field named "
                                            + arrayFieldName + " in document type " + documentTypeName
                                            + " is not an array of Document.");
        }
    }

    static boolean doProcessOuterDocument(Object o, String documentTypeName) {
        if ( ! (o instanceof DocumentOperation)) {
            if (log.isLoggable(Level.FINE)) {
                log.log(Level.FINE, o + " is not a DocumentOperation.");
            }
            return false;
        }

        DocumentOperation outerDocOp = (DocumentOperation)o;
        if ( ! (outerDocOp instanceof DocumentPut)) {
            //this is not a put, return
            if (log.isLoggable(Level.FINE)) {
                log.log(Level.FINE, "Given DocumentOperation is not a DocumentPut, returning. (Was given "
                                        + outerDocOp + ").");
            }
            return false;
        }

        Document outerDoc = ((DocumentPut) outerDocOp).getDocument();
        DocumentType type = outerDoc.getDataType();
        if (!type.getName().equalsIgnoreCase(documentTypeName)) {
            //this is not the right document type
            if (log.isLoggable(Level.FINE)) {
                log.log(Level.FINE, "Given Document is of wrong type, returning. (Was given " + outerDoc + ").");
            }
            return false;
        }
        return true;
    }

}
