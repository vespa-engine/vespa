// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.common.annotations.Beta;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.TestAndSetCondition;
import com.yahoo.document.json.document.DocumentParser;
import com.yahoo.document.json.readers.DocumentParseInfo;
import com.yahoo.document.json.readers.VespaJsonDocumentReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static com.yahoo.document.json.JsonReader.ReaderState.END_OF_FEED;
import static com.yahoo.document.json.readers.JsonParserHelpers.expectArrayStart;

/**
 * Initialize Vespa documents/updates/removes from an InputStream containing a
 * valid JSON representation of a feed.
 *
 * @author Steinar Knutsen
 * @author dybis
 */
public class JsonReader {

    public Optional<DocumentParseInfo> parseDocument() throws IOException {
        DocumentParser documentParser = new DocumentParser(parser);
        return documentParser.parse(Optional.empty());
    }

    private final JsonParser parser;
    private final DocumentTypeManager typeManager;
    private ReaderState state = ReaderState.AT_START;

    enum ReaderState {
        AT_START, READING, END_OF_FEED
    }

    public JsonReader(DocumentTypeManager typeManager, InputStream input, JsonFactory parserFactory) {
        this.typeManager = typeManager;

        try {
            parser = parserFactory.createParser(input);
        } catch (IOException e) {
            state = END_OF_FEED;
            throw new RuntimeException(e);
        }
    }

    /**
     * Reads a single operation. The operation is not expected to be part of an array.
     * @param operationType the type of operation (update or put)
     * @param docIdString document ID.
     * @return the document
     */
    public DocumentOperation readSingleDocument(DocumentParser.SupportedOperation operationType, String docIdString) {
        DocumentId docId = new DocumentId(docIdString);
        final DocumentParseInfo documentParseInfo;
        try {
            DocumentParser documentParser = new DocumentParser(parser);
            documentParseInfo = documentParser.parse(Optional.of(docId)).get();
        } catch (IOException e) {
            state = END_OF_FEED;
            throw new RuntimeException(e);
        }
        documentParseInfo.operationType = operationType;
        VespaJsonDocumentReader vespaJsonDocumentReader = new VespaJsonDocumentReader();
        DocumentOperation operation = vespaJsonDocumentReader.createDocumentOperation(
                getDocumentTypeFromString(documentParseInfo.documentId.getDocType(), typeManager), documentParseInfo);
        operation.setCondition(TestAndSetCondition.fromConditionString(documentParseInfo.condition));
        return operation;
    }

    /** Returns the next document operation, or null if we have reached the end */
    public DocumentOperation next() {
        switch (state) {
            case AT_START:
                JsonToken t = nextToken(parser);
                expectArrayStart(t);
                state = ReaderState.READING;
                break;
            case END_OF_FEED:
                return null;
            case READING:
                break;
        }
        Optional<DocumentParseInfo> documentParseInfo;
        try {
            documentParseInfo = parseDocument();
        } catch (IOException r) {
            // Jackson is not able to recover from structural parse errors
            state = END_OF_FEED;
            throw new RuntimeException(r);
        }
        if (! documentParseInfo.isPresent()) {
            state = END_OF_FEED;
            return null;
        }
        VespaJsonDocumentReader vespaJsonDocumentReader = new VespaJsonDocumentReader();
        DocumentOperation operation = vespaJsonDocumentReader.createDocumentOperation(
                getDocumentTypeFromString(documentParseInfo.get().documentId.getDocType(), typeManager),
                documentParseInfo.get());
        operation.setCondition(TestAndSetCondition.fromConditionString(documentParseInfo.get().condition));
        return operation;
    }


    public DocumentType readDocumentType(DocumentId docId) {
        return getDocumentTypeFromString(docId.getDocType(), typeManager);
    }

    private static DocumentType getDocumentTypeFromString(String docTypeString, DocumentTypeManager typeManager) {
        final DocumentType docType = typeManager.getDocumentType(docTypeString);
        if (docType == null) {
            throw new IllegalArgumentException(String.format("Document type %s does not exist", docTypeString));
        }
        return docType;
    }

    public JsonToken nextToken(JsonParser parser) {
        try {
            return parser.nextValue();
        } catch (IOException e) {
            // Jackson is not able to recover from structural parse errors
            state = END_OF_FEED;
            throw new RuntimeException(e);
        }
    }
}
