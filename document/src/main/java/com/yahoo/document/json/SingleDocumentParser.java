// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.json.document.DocumentParser;
import com.yahoo.vespaxmlparser.DocumentFeedOperation;
import com.yahoo.vespaxmlparser.DocumentUpdateFeedOperation;
import com.yahoo.vespaxmlparser.FeedOperation;

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

    public FeedOperation parsePut(InputStream inputStream, String docId) {
        return parse(inputStream, docId, DocumentParser.SupportedOperation.PUT);
    }

    public FeedOperation parseUpdate(InputStream inputStream, String docId)  {
        return parse(inputStream, docId, DocumentParser.SupportedOperation.UPDATE);
    }

    private FeedOperation parse(InputStream inputStream, String docId, DocumentParser.SupportedOperation supportedOperation)  {
        JsonReader reader = new JsonReader(docMan, inputStream, jsonFactory);
        DocumentOperation documentOperation = reader.readSingleDocument(supportedOperation, docId);
        try {
            inputStream.close();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        if (supportedOperation == DocumentParser.SupportedOperation.PUT) {
            return new DocumentFeedOperation(((DocumentPut) documentOperation).getDocument(), documentOperation.getCondition());
        } else {
            return new DocumentUpdateFeedOperation((DocumentUpdate) documentOperation, documentOperation.getCondition());
        }
    }

}
