// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.json.document.DocumentParser;
import com.yahoo.vespaxmlparser.VespaXMLFeedReader;

import java.io.IOException;
import java.io.InputStream;

/**
 * Parser that supports parsing PUT operation and UPDATE operation.
 *
 * @author dybis
 */
public class SingleDocumentParser {
    private static final JsonFactory jsonFactory = new JsonFactory().disable(JsonFactory.Feature.CANONICALIZE_FIELD_NAMES);
    private DocumentTypeManager docMan;

    public SingleDocumentParser(DocumentTypeManager docMan) {
        this.docMan = docMan;
    }

    public VespaXMLFeedReader.Operation parsePut(InputStream inputStream, String docId) {
        return parse(inputStream, docId, DocumentParser.SupportedOperation.PUT);
    }

    public VespaXMLFeedReader.Operation parseUpdate(InputStream inputStream, String docId)  {
        return parse(inputStream, docId, DocumentParser.SupportedOperation.UPDATE);
    }

    private VespaXMLFeedReader.Operation parse(InputStream inputStream, String docId, DocumentParser.SupportedOperation supportedOperation)  {
        final JsonReader reader = new JsonReader(docMan, inputStream, jsonFactory);
        final DocumentOperation documentOperation = reader.readSingleDocument(supportedOperation, docId);
        VespaXMLFeedReader.Operation operation = new VespaXMLFeedReader.Operation();
        try {
            inputStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (supportedOperation == DocumentParser.SupportedOperation.PUT) {
            operation.setDocument(((DocumentPut) documentOperation).getDocument());
        } else {
            operation.setDocumentUpdate((DocumentUpdate) documentOperation);
        }

        // (A potentially empty) test-and-set condition is always set by JsonReader
        operation.setCondition(documentOperation.getCondition());

        return operation;
    }
}
