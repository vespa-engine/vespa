// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.Field;
import com.yahoo.document.TestAndSetCondition;
import com.yahoo.document.json.document.DocumentParser;
import com.yahoo.document.json.readers.DocumentParseInfo;
import com.yahoo.document.update.FieldUpdate;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static com.yahoo.document.json.JsonReader.ReaderState.END_OF_FEED;
import static com.yahoo.document.json.readers.AddRemoveCreator.createAdds;
import static com.yahoo.document.json.readers.AddRemoveCreator.createRemoves;
import static com.yahoo.document.json.readers.CompositeReader.populateComposite;
import static com.yahoo.document.json.readers.JsonParserHelpers.expectArrayStart;
import static com.yahoo.document.json.readers.JsonParserHelpers.expectObjectStart;
import static com.yahoo.document.json.readers.MapReader.UPDATE_MATCH;
import static com.yahoo.document.json.readers.MapReader.createMapUpdate;
import static com.yahoo.document.json.readers.SingleValueReader.readSingleUpdate;

/**
 * Initialize Vespa documents/updates/removes from an InputStream containing a
 * valid JSON representation of a feed.
 *
 * @author Steinar Knutsen
 * @author dybis
 */
@Beta
public class JsonReader {

    public Optional<DocumentParseInfo> parseDocument() throws IOException {
        DocumentParser documentParser = new DocumentParser(parser);
        return documentParser.parse(Optional.empty());
    }

    private static final String UPDATE_REMOVE = "remove";
    private static final String UPDATE_ADD = "add";

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
     * Reads a single operation. The operation is not expected to be part of an array. It only reads FIELDS.
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
        DocumentOperation operation = createDocumentOperation(documentParseInfo);
        operation.setCondition(TestAndSetCondition.fromConditionString(documentParseInfo.condition));
        return operation;
    }

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
        DocumentOperation operation = createDocumentOperation(documentParseInfo.get());
        operation.setCondition(TestAndSetCondition.fromConditionString(documentParseInfo.get().condition));
        return operation;
    }

    private DocumentOperation createDocumentOperation(DocumentParseInfo documentParseInfo) {
        DocumentType documentType = getDocumentTypeFromString(documentParseInfo.documentId.getDocType(), typeManager);
        final DocumentOperation documentOperation;
        try {
            switch (documentParseInfo.operationType) {
                case PUT:
                    documentOperation = new DocumentPut(new Document(documentType, documentParseInfo.documentId));
                    readPut(documentParseInfo.fieldsBuffer, (DocumentPut) documentOperation);
                    verifyEndState(documentParseInfo.fieldsBuffer, JsonToken.END_OBJECT);
                    break;
                case REMOVE:
                    documentOperation = new DocumentRemove(documentParseInfo.documentId);
                    break;
                case UPDATE:
                    documentOperation = new DocumentUpdate(documentType, documentParseInfo.documentId);
                    if (documentParseInfo.fieldsBuffer.size() == 0 && documentParseInfo.fieldpathsBuffer.size() == 0) {
                        throw new IllegalArgumentException("Either 'fields' or 'fieldpaths' must be set");
                    }

                    if (documentParseInfo.fieldsBuffer.size() > 0) {
                        readUpdate(documentParseInfo.fieldsBuffer, (DocumentUpdate) documentOperation);
                        verifyEndState(documentParseInfo.fieldsBuffer, JsonToken.END_OBJECT);
                    }
                    if (documentParseInfo.fieldpathsBuffer.size() > 0) {
                        VespaJsonDocumentReader vespaJsonDocumentReader = new VespaJsonDocumentReader(documentType, documentParseInfo);
                        vespaJsonDocumentReader.read((DocumentUpdate) documentOperation);
                        verifyEndState(documentParseInfo.fieldpathsBuffer, JsonToken.END_ARRAY);
                    }
                    break;
                default:
                    throw new IllegalStateException("Implementation out of sync with itself. This is a bug.");
            }
        } catch (JsonReaderException e) {
            throw JsonReaderException.addDocId(e, documentParseInfo.documentId);
        }
        if (documentParseInfo.create.isPresent()) {
            if (!(documentOperation instanceof DocumentUpdate)) {
                throw new RuntimeException("Could not set create flag on non update operation.");
            }
            DocumentUpdate update = (DocumentUpdate) documentOperation;
            update.setCreateIfNonExistent(documentParseInfo.create.get());
        }
        return documentOperation;
    }

    // Exposed for unit testing...
    void readUpdate(TokenBuffer buffer, DocumentUpdate next) {
        if (buffer.size() == 0) {
            buffer.bufferObject(nextToken(parser), parser);
        }
        populateUpdateFromBuffer(buffer, next);
    }

    // Exposed for unit testing...
    void readPut(TokenBuffer buffer, DocumentPut put) {
        if (buffer.size() == 0) {
            buffer.bufferObject(nextToken(parser), parser);
        }
        try {
            populateComposite(buffer, put.getDocument());
        } catch (JsonReaderException e) {
            throw JsonReaderException.addDocId(e, put.getId());
        }
    }

    private void verifyEndState(TokenBuffer buffer, JsonToken expectedFinalToken) {
        Preconditions.checkState(buffer.currentToken() == expectedFinalToken,
                "Expected end of JSON struct (%s), got %s", expectedFinalToken, buffer.currentToken());
        Preconditions.checkState(buffer.nesting() == 0, "Nesting not zero at end of operation");
        Preconditions.checkState(buffer.next() == null, "Dangling data at end of operation");
        Preconditions.checkState(buffer.size() == 0, "Dangling data at end of operation");
    }

    private static void populateUpdateFromBuffer(TokenBuffer buffer, DocumentUpdate update) {
        expectObjectStart(buffer.currentToken());
        int localNesting = buffer.nesting();
        JsonToken t = buffer.next();

        while (localNesting <= buffer.nesting()) {
            expectObjectStart(t);
            String fieldName = buffer.currentName();
            Field field = update.getType().getField(fieldName);
            addFieldUpdates(buffer, update, field);
            t = buffer.next();
        }
    }

    private static void addFieldUpdates(TokenBuffer buffer, DocumentUpdate update, Field field) {
        int localNesting = buffer.nesting();
        FieldUpdate fieldUpdate = FieldUpdate.create(field);

        buffer.next();
        while (localNesting <= buffer.nesting()) {
            switch (buffer.currentName()) {
            case UPDATE_REMOVE:
                createRemoves(buffer, field, fieldUpdate);
                break;
            case UPDATE_ADD:
                createAdds(buffer, field, fieldUpdate);
                break;
            case UPDATE_MATCH:
                fieldUpdate.addValueUpdate(createMapUpdate(buffer, field));
                break;
            default:
                String action = buffer.currentName();
                fieldUpdate.addValueUpdate(readSingleUpdate(buffer, field.getDataType(), action));
            }
            buffer.next();
        }
        update.addFieldUpdate(fieldUpdate);
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
