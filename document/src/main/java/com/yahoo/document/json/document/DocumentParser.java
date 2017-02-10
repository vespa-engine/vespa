// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json.document;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.yahoo.document.DocumentId;
import com.yahoo.document.json.readers.DocumentParseInfo;

import java.io.IOException;
import java.util.Optional;

import static com.yahoo.document.json.readers.JsonParserHelpers.expectObjectStart;

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
    public static final String FIELDPATHS = "fieldpaths";
    public static final String REMOVE = "remove";

    public static Optional<DocumentParseInfo> parseDocument(JsonParser parser) throws IOException {
        // we should now be at the start of a feed operation or at the end of the feed
        JsonToken token = parser.nextValue();
        if (token == JsonToken.END_ARRAY) {
            return Optional.empty(); // end of feed
        }
        expectObjectStart(token);

        DocumentParseInfo documentParseInfo = new DocumentParseInfo();

        while (true) {
            try {
                token = parser.nextValue();
                if ((token == JsonToken.VALUE_TRUE || token == JsonToken.VALUE_FALSE) &&
                        CREATE_IF_NON_EXISTENT.equals(parser.getCurrentName())) {
                    documentParseInfo.create = Optional.of(token == JsonToken.VALUE_TRUE);
                    continue;
                }
                if (token == JsonToken.VALUE_STRING && CONDITION.equals(parser.getCurrentName())) {
                    documentParseInfo.condition = Optional.of(parser.getText());
                    continue;
                }
                if (token == JsonToken.START_OBJECT) {
                    try {
                        if (!FIELDS.equals(parser.getCurrentName())) {
                            throw new IllegalArgumentException("Unexpected object key: " + parser.getCurrentName());
                        }
                    } catch (IOException e) {
                        // TODO more specific wrapping
                        throw new RuntimeException(e);
                    }
                    documentParseInfo.fieldsBuffer.bufferObject(token, parser);
                    continue;
                }
                if (token == JsonToken.END_OBJECT) {
                    if (documentParseInfo.documentId == null) {
                        throw new RuntimeException("Did not find document operation");
                    }
                    return Optional.of(documentParseInfo);
                }
                if (token == JsonToken.VALUE_STRING) {
                    documentParseInfo.operationType = operationNameToOperationType(parser.getCurrentName());
                    documentParseInfo.documentId = new DocumentId(parser.getText());
                    continue;
                }
                throw new RuntimeException("Expected document start or document operation.");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
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

    public static DocumentParseInfo parseDocumentsFields(JsonParser parser, DocumentId documentId) throws IOException {
        long indentLevel = 0;
        DocumentParseInfo documentParseInfo = new DocumentParseInfo();
        documentParseInfo.documentId = documentId;
        while (true) {
            // we should now be at the start of a feed operation or at the end of the feed
            JsonToken t = parser.nextValue();
            if (t == null) {
                throw new IllegalArgumentException("Could not read document, no document?");
            }
            switch (t) {
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
            try {
                if (indentLevel == 0L && t == JsonToken.END_OBJECT) {
                    break;
                } else if (indentLevel == 1L && (t == JsonToken.VALUE_TRUE || t == JsonToken.VALUE_FALSE)) {
                    if (CREATE_IF_NON_EXISTENT.equals(parser.getCurrentName())) {
                        documentParseInfo.create = Optional.ofNullable(parser.getBooleanValue());
                    }
                } else if (indentLevel == 2L && t == JsonToken.START_OBJECT && FIELDS.equals(parser.getCurrentName())) {
                    documentParseInfo.fieldsBuffer.bufferObject(t, parser);
                    indentLevel--;
                } else if (indentLevel == 10001L && t == JsonToken.START_ARRAY && FIELDPATHS.equals(parser.getCurrentName())) {
                    documentParseInfo.fieldpathsBuffer.bufferArray(t, parser);
                    indentLevel -= 10000L;
                }
            } catch (IOException e) {
                throw new RuntimeException("Got IO exception while parsing document", e);
            }
        }

        return documentParseInfo;
    }
}
