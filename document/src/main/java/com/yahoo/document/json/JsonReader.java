// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.TestAndSetCondition;
import com.yahoo.document.json.document.DocumentParser;
import com.yahoo.document.json.readers.DocumentParseInfo;
import com.yahoo.document.json.readers.VespaJsonDocumentReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static com.yahoo.document.json.JsonReader.ReaderState.END_OF_FEED;
import static com.yahoo.document.json.document.DocumentParser.CONDITION;
import static com.yahoo.document.json.document.DocumentParser.CREATE_IF_NON_EXISTENT;
import static com.yahoo.document.json.document.DocumentParser.FIELDS;
import static com.yahoo.document.json.readers.JsonParserHelpers.expectArrayStart;

/**
 * Initialize Vespa documents/updates/removes from an InputStream containing a
 * valid JSON representation of a feed.
 *
 * @author Steinar Knutsen
 * @author Haakon Dybdahl
 */
public class JsonReader {

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
            throw new IllegalArgumentException(e);
        }
    }

    public Optional<DocumentParseInfo> parseDocument() throws IOException {
        DocumentParser documentParser = new DocumentParser(parser);
        return documentParser.parse(Optional.empty());
    }

    /**
     * Reads a single operation. The operation is not expected to be part of an array.
     *
     * @param operationType the type of operation (update or put)
     * @param docIdString document ID
     * @return the parsed document operation
     */
    ParsedDocumentOperation readSingleDocument(DocumentOperationType operationType, String docIdString) {
        DocumentId docId = new DocumentId(docIdString);
        DocumentParseInfo documentParseInfo;
        try {
            DocumentParser documentParser = new DocumentParser(parser);
            documentParseInfo = documentParser.parse(Optional.of(docId)).get();
        } catch (IOException e) {
            state = END_OF_FEED;
            throw new IllegalArgumentException(e);
        }
        documentParseInfo.operationType = operationType;
        VespaJsonDocumentReader vespaJsonDocumentReader = new VespaJsonDocumentReader(typeManager.getIgnoreUndefinedFields());
        ParsedDocumentOperation operation = vespaJsonDocumentReader.createDocumentOperation(
                getDocumentTypeFromString(documentParseInfo.documentId.getDocType(), typeManager), documentParseInfo);
        operation.operation().setCondition(TestAndSetCondition.fromConditionString(documentParseInfo.condition));
        return operation;
    }

    /**
     * Reads a JSON which is expected to contain a single document operation,
     * and where other parameters, like the document ID and operation type, are supplied by other means.
     *
     * @param operationType the type of operation (update or put)
     * @param docIdString document ID
     * @return the parsed document operation
     */
    public ParsedDocumentOperation readSingleDocumentStreaming(DocumentOperationType operationType, String docIdString) {
        try {
            DocumentId docId = new DocumentId(docIdString);
            DocumentParseInfo documentParseInfo = new DocumentParseInfo();
            documentParseInfo.documentId = docId;
            documentParseInfo.operationType = operationType;

            if (JsonToken.START_OBJECT != parser.nextValue())
                throw new IllegalArgumentException("expected start of root object, got " + parser.currentToken());

            Boolean create = null;
            String condition = null;
            ParsedDocumentOperation operation = null;
            while (JsonToken.END_OBJECT != parser.nextValue()) {
                switch (parser.currentName()) {
                    case FIELDS -> {
                        documentParseInfo.fieldsBuffer = new LazyTokenBuffer(parser);
                        VespaJsonDocumentReader vespaJsonDocumentReader = new VespaJsonDocumentReader(typeManager.getIgnoreUndefinedFields());
                        operation = vespaJsonDocumentReader.createDocumentOperation(
                                getDocumentTypeFromString(documentParseInfo.documentId.getDocType(), typeManager), documentParseInfo);

                        if ( ! documentParseInfo.fieldsBuffer.isEmpty())
                            throw new IllegalArgumentException("expected all content to be consumed by document parsing, but " +
                                                               documentParseInfo.fieldsBuffer.nesting() + " levels remain");

                    }
                    case CONDITION -> {
                        if ( ! JsonToken.VALUE_STRING.equals(parser.currentToken()) && ! JsonToken.VALUE_NULL.equals(parser.currentToken()))
                            throw new IllegalArgumentException("expected string value for condition, got " + parser.currentToken());

                        condition = parser.getValueAsString();
                    }
                    case CREATE_IF_NON_EXISTENT -> {
                        create = parser.getBooleanValue(); // Throws if not boolean.
                    }
                    default -> {
                        // We ignore stray fields, but need to ensure structural balance in doing do.
                        if (parser.currentToken().isStructStart()) parser.skipChildren();
                    }
                }
            }

            if (null != parser.nextToken())
                throw new IllegalArgumentException("expected end of input, got " + parser.currentToken());

            if (null == operation)
                throw new IllegalArgumentException("document is missing the required \"fields\" field");

            if (null != create) {
                switch (operationType) {
                    case PUT -> ((DocumentPut) operation.operation()).setCreateIfNonExistent(create);
                    case UPDATE -> ((DocumentUpdate) operation.operation()).setCreateIfNonExistent(create);
                    case REMOVE -> throw new IllegalArgumentException(CREATE_IF_NON_EXISTENT + " is not supported for remove operations");
                }
            }

            operation.operation().setCondition(TestAndSetCondition.fromConditionString(Optional.ofNullable(condition)));

            return operation;
        }
        catch (IOException e) {
            throw new IllegalArgumentException("failed parsing document", e);
        }
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
            throw new IllegalArgumentException(r);
        }
        if (documentParseInfo.isEmpty()) {
            state = END_OF_FEED;
            return null;
        }
        VespaJsonDocumentReader vespaJsonDocumentReader = new VespaJsonDocumentReader(typeManager.getIgnoreUndefinedFields());
        DocumentOperation operation = vespaJsonDocumentReader.createDocumentOperation(
                getDocumentTypeFromString(documentParseInfo.get().documentId.getDocType(), typeManager),
                documentParseInfo.get()).operation();
        operation.setCondition(TestAndSetCondition.fromConditionString(documentParseInfo.get().condition));
        return operation;
    }


    public DocumentType readDocumentType(DocumentId docId) {
        return getDocumentTypeFromString(docId.getDocType(), typeManager);
    }

    private static DocumentType getDocumentTypeFromString(String docTypeString, DocumentTypeManager typeManager) {
        final DocumentType docType = typeManager.getDocumentType(docTypeString);
        if (docType == null)
            throw new IllegalArgumentException(String.format("Document type %s does not exist", docTypeString));
        return docType;
    }

    public JsonToken nextToken(JsonParser parser) {
        try {
            return parser.nextValue();
        } catch (IOException e) {
            // Jackson is not able to recover from structural parse errors
            state = END_OF_FEED;
            throw new IllegalArgumentException(e);
        }
    }

}
