// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.Field;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.ReferenceDataType;
import com.yahoo.document.TestAndSetCondition;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StructuredFieldValue;
import com.yahoo.document.json.document.DocumentParser;
import com.yahoo.document.json.readers.DocumentParseInfo;
import com.yahoo.document.json.readers.DocumentToFields;
import com.yahoo.document.json.readers.MapReader;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.document.update.MapValueUpdate;
import com.yahoo.document.update.ValueUpdate;
import org.apache.commons.codec.binary.Base64;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static com.yahoo.document.json.readers.AddRemoveCreator.createAddsOrRemoves;
import static com.yahoo.document.json.readers.CompositeReader.populateComposite;
import static com.yahoo.document.json.readers.SingleValueReader.readSingleUpdate;

/**
 * Initialize Vespa documents/updates/removes from an InputStream containing a
 * valid JSON representation of a feed.
 *
 * @author Steinar Knutsen
 * @since 5.1.25
 */
@Beta
public class JsonReader {

    public Optional<DocumentParseInfo> parseDocument() {
        return DocumentParser.parseDocument(parser);
    }

    public enum FieldOperation {
        ADD, REMOVE
    }
    public static final String FIELDS = "fields";
    public static final String REMOVE = "remove";

    public static final String CONDITION = "condition";
    public static final String CREATE_IF_NON_EXISTENT = "create";
    private static final String UPDATE_REMOVE = "remove";
    public static final String UPDATE_MATCH = "match";
    private static final String UPDATE_ADD = "add";
    public static final String UPDATE_ELEMENT = "element";

    private final JsonParser parser;
    private final DocumentTypeManager typeManager;
    private ReaderState state = ReaderState.AT_START;

    public enum SupportedOperation {
        PUT, UPDATE, REMOVE
    }

    enum ReaderState {
        AT_START, READING, END_OF_FEED
    }

    public JsonReader(DocumentTypeManager typeManager, InputStream input, JsonFactory parserFactory) {
        this.typeManager = typeManager;

        try {
            parser = parserFactory.createParser(input);
        } catch (IOException e) {
            state = ReaderState.END_OF_FEED;
            throw new RuntimeException(e);
        }
    }

    /**
     * Reads a single operation. The operation is not expected to be part of an array. It only reads FIELDS.
     * @param operationType the type of operation (update or put)
     * @param docIdString document ID.
     * @return the document
     */
    public DocumentOperation readSingleDocument(SupportedOperation operationType, String docIdString) {
        DocumentId docId = new DocumentId(docIdString);
        DocumentParseInfo documentParseInfo = DocumentToFields.parseToDocumentsFieldsAndInsertFieldsIntoBuffer(parser, docId);
        documentParseInfo.operationType = operationType;
        DocumentOperation operation = createDocumentOperation(documentParseInfo.fieldsBuffer, documentParseInfo);
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

        Optional<DocumentParseInfo> documentParseInfo = DocumentParser.parseDocument(parser);

        if (! documentParseInfo.isPresent()) {
            state = ReaderState.END_OF_FEED;
            return null;
        }
        DocumentOperation operation = createDocumentOperation(documentParseInfo.get().fieldsBuffer, documentParseInfo.get());
        operation.setCondition(TestAndSetCondition.fromConditionString(documentParseInfo.get().condition));
        return operation;
    }

    private DocumentOperation createDocumentOperation(TokenBuffer buffer, DocumentParseInfo documentParseInfo) {
        DocumentType documentType = getDocumentTypeFromString(documentParseInfo.documentId.getDocType(), typeManager);
        final DocumentOperation documentOperation;
        try {
            switch (documentParseInfo.operationType) {
                case PUT:
                    documentOperation = new DocumentPut(new Document(documentType, documentParseInfo.documentId));
                    readPut(buffer, (DocumentPut) documentOperation);
                    verifyEndState(buffer);
                    break;
                case REMOVE:
                    documentOperation = new DocumentRemove(documentParseInfo.documentId);
                    break;
                case UPDATE:
                    documentOperation = new DocumentUpdate(documentType, documentParseInfo.documentId);
                    readUpdate(buffer, (DocumentUpdate) documentOperation);
                    verifyEndState(buffer);
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

    void readUpdate(TokenBuffer buffer, DocumentUpdate next) {
        if (buffer.size() == 0) {
            bufferFields(parser, buffer, nextToken(parser));
        }
        populateUpdateFromBuffer(buffer, next);
    }

    void readPut(TokenBuffer buffer, DocumentPut put) {
        if (buffer.size() == 0) {
            bufferFields(parser, buffer, nextToken(parser));
        }
        JsonToken t = buffer.currentToken();
        try {
            populateComposite(buffer, put.getDocument(), t);
        } catch (JsonReaderException e) {
            throw JsonReaderException.addDocId(e, put.getId());
        }
    }

    private void verifyEndState(TokenBuffer buffer) {
        Preconditions.checkState(buffer.nesting() == 0, "Nesting not zero at end of operation");
        expectObjectEnd(buffer.currentToken());
        Preconditions.checkState(buffer.next() == null, "Dangling data at end of operation");
        Preconditions.checkState(buffer.size() == 0, "Dangling data at end of operation");
    }

    private void populateUpdateFromBuffer(TokenBuffer buffer, DocumentUpdate update) {
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

    private void addFieldUpdates(TokenBuffer buffer, DocumentUpdate update, Field field) {
        int localNesting = buffer.nesting();
        FieldUpdate fieldUpdate = FieldUpdate.create(field);

        buffer.next();
        while (localNesting <= buffer.nesting()) {
            switch (buffer.currentName()) {
            case UPDATE_REMOVE:
                createAddsOrRemoves(buffer, field, fieldUpdate, FieldOperation.REMOVE);
                break;
            case UPDATE_ADD:
                createAddsOrRemoves(buffer, field, fieldUpdate, FieldOperation.ADD);
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

    @SuppressWarnings("rawtypes")
    public ValueUpdate createMapUpdate(TokenBuffer buffer, Field field) {
        buffer.next();
        MapValueUpdate m = (MapValueUpdate) MapReader.createMapUpdate(buffer, field.getDataType(), null, null);
        buffer.next();
        // must generate the field value in parallell with the actual
        return m;

    }

    public static FieldValue NEWpopulateComposite(DataType dataType, JsonParser parser) throws IOException {
        // bla
        JsonToken t = parser.nextToken();
        return null;
    }


    public static void expectCompositeEnd(JsonToken token) {
        Preconditions.checkState(token.isStructEnd(), "Expected end of composite, got %s", token);
    }

    public static Field getField(TokenBuffer buffer, StructuredFieldValue parent) {
        Field f = parent.getField(buffer.currentName());
        if (f == null) {
            throw new NullPointerException("Could not get field \"" + buffer.currentName() +
                    "\" in the structure of type \"" + parent.getDataType().getDataTypeName() + "\".");
        }
        return f;
    }

    public static void expectArrayStart(JsonToken token) {
        Preconditions.checkState(token == JsonToken.START_ARRAY, "Expected start of array, got %s", token);
    }

    public static void expectObjectStart(JsonToken token) {
        Preconditions.checkState(token == JsonToken.START_OBJECT, "Expected start of JSON object, got %s", token);
    }

    public static void expectObjectEnd(JsonToken token) {
        Preconditions.checkState(token == JsonToken.END_OBJECT, "Expected end of JSON object, got %s", token);
    }

    public static FieldValue readAtomic(TokenBuffer buffer, DataType expectedType) {
        if (expectedType.equals(DataType.RAW)) {
            return expectedType.createFieldValue(new Base64().decode(buffer.currentText()));
        } else if (expectedType.equals(PositionDataType.INSTANCE)) {
            return PositionDataType.fromString(buffer.currentText());
        } else if (expectedType instanceof ReferenceDataType) {
            return readReferenceFieldValue(buffer, expectedType);
        } else {
            return expectedType.createFieldValue(buffer.currentText());
        }
    }

    private static FieldValue readReferenceFieldValue(TokenBuffer buffer, DataType expectedType) {
        final FieldValue value = expectedType.createFieldValue();
        final String refText = buffer.currentText();
        if (!refText.isEmpty()) {
            value.assign(new DocumentId(buffer.currentText()));
        }
        return value;
    }

    public static void bufferFields(JsonParser parser, TokenBuffer buffer, JsonToken current) {
        buffer.bufferObject(current, parser);
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

    public static JsonToken nextToken(JsonParser parser) {
        try {
            return parser.nextValue();
        } catch (IOException e) {
            // Jackson is not able to recover from structural parse errors
            // TODO Do we really need to set state on exception?
            // state = ReaderState.END_OF_FEED;
            throw new RuntimeException(e);
        }
    }
}
