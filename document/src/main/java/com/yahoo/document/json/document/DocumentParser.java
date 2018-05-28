// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json.document;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.yahoo.document.DocumentId;
import com.yahoo.document.json.readers.DocumentParseInfo;

import java.io.IOException;
import java.util.Optional;

/**
 * Parses a document operation.
 *
 * @author dybis
 */
public class DocumentParser {

    public enum SupportedOperation {
        PUT, UPDATE, REMOVE
    }
    private static final String UPDATE = "update";
    private static final String PUT = "put";
    private static final String ID = "id";
    private static final String CONDITION = "condition";
    public static final String CREATE_IF_NON_EXISTENT = "create";
    public static final String FIELDS = "fields";
    public static final String REMOVE = "remove";
    private final JsonParser parser;
    private  long indentLevel;

    public DocumentParser(JsonParser parser) {
        this.parser = parser;
    }

    /**
     * Parses a single document and returns it.
     * Returns empty is we have reached the end of the stream.
     */
    public Optional<DocumentParseInfo> parse(Optional<DocumentId> documentIdArg) throws IOException {
        indentLevel = 0;
        DocumentParseInfo documentParseInfo = new DocumentParseInfo();
        documentIdArg.ifPresent(documentId -> documentParseInfo.documentId = documentId);
        boolean foundItems = false;
        do {
            foundItems |= parseOneItem(documentParseInfo, documentIdArg.isPresent() /* doc id set externally */);
        } while (indentLevel > 0L);

        if (documentParseInfo.documentId == null) {
            if (foundItems)
                throw new IllegalArgumentException("Missing a document operation ('put', 'update' or 'remove')");
            else
                return Optional.empty();
        }
        return Optional.of(documentParseInfo);
    }

    /**
     * Parses one item from the stream.
     *
     * @return whether an item was found
     */
    private boolean parseOneItem(DocumentParseInfo documentParseInfo, boolean docIdAndOperationIsSetExternally) throws IOException {
        parser.nextValue();
        processIndent();
        if (parser.getCurrentName() == null) return false;
        if (indentLevel == 1L) {
            handleIdentLevelOne(documentParseInfo, docIdAndOperationIsSetExternally);
        } else if (indentLevel == 2L) {
            handleIdentLevelTwo(documentParseInfo);
        }
        return true;
    }

    private void processIndent() {
        JsonToken currentToken = parser.currentToken();
        if (currentToken == null) {
            throw new IllegalArgumentException("Could not read document, no document?");
        }
        switch (currentToken) {
            case START_OBJECT:
                indentLevel++;
                break;
            case END_OBJECT:
                indentLevel--;
                break;
            case START_ARRAY:
                indentLevel += 10000L;
                break;
            case END_ARRAY:
                indentLevel -= 10000L;
                break;
        }
    }

    private void handleIdentLevelOne(DocumentParseInfo documentParseInfo, boolean docIdAndOperationIsSetExternally)
            throws IOException {
        JsonToken currentToken = parser.getCurrentToken();
        if (currentToken == JsonToken.VALUE_TRUE || currentToken == JsonToken.VALUE_FALSE) {
            try {
                if (CREATE_IF_NON_EXISTENT.equals(parser.getCurrentName())) {
                    documentParseInfo.create = Optional.ofNullable(parser.getBooleanValue());
                    return;
                }
            } catch (IOException e) {
                throw new RuntimeException("Got IO exception while parsing document", e);
            }
        }
        if ((currentToken == JsonToken.VALUE_TRUE || currentToken == JsonToken.VALUE_FALSE) &&
                CREATE_IF_NON_EXISTENT.equals(parser.getCurrentName())) {
            documentParseInfo.create = Optional.of(currentToken == JsonToken.VALUE_TRUE);
        } else if (currentToken == JsonToken.VALUE_STRING && CONDITION.equals(parser.getCurrentName())) {
            documentParseInfo.condition = Optional.of(parser.getText());
        } else if (currentToken == JsonToken.VALUE_STRING) {
            // Value is expected to be set in the header not in the document. Ignore any unknown field
            // as well.
            if (! docIdAndOperationIsSetExternally) {
                documentParseInfo.operationType = operationNameToOperationType(parser.getCurrentName());
                documentParseInfo.documentId = new DocumentId(parser.getText());
            }
        }
    }

    private  void handleIdentLevelTwo(DocumentParseInfo documentParseInfo) {
        try {
            JsonToken currentToken = parser.getCurrentToken();
            // "fields" opens a dictionary and is therefore on level two which might be surprising.
            if (currentToken == JsonToken.START_OBJECT && FIELDS.equals(parser.getCurrentName())) {
                documentParseInfo.fieldsBuffer.bufferObject(currentToken, parser);
                processIndent();
            }
        } catch (IOException e) {
            throw new RuntimeException("Got IO exception while parsing document", e);
        }
    }

    private static SupportedOperation operationNameToOperationType(String operationName) {
        switch (operationName) {
            case PUT:
            case ID:
                return SupportedOperation.PUT;
            case REMOVE:
                return SupportedOperation.REMOVE;
            case UPDATE:
                return SupportedOperation.UPDATE;
            default:
                throw new IllegalArgumentException(
                        "Got " + operationName + " as document operation, only \"put\", " +
                                "\"remove\" and \"update\" are supported.");
        }
    }
}
