// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json.document;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.yahoo.document.DocumentId;
import com.yahoo.document.json.readers.DocumentParseInfo;

import java.io.IOException;
import java.util.Optional;

import static com.yahoo.document.json.JsonReader.*;

public class DocumentParser {
    private static final String UPDATE = "update";
    private static final String PUT = "put";
    private static final String ID = "id";

    public static Optional<DocumentParseInfo> parseDocument(JsonParser parser) {
        // we should now be at the start of a feed operation or at the end of the feed
        JsonToken token = nextToken(parser);
        if (token == JsonToken.END_ARRAY) {
            return Optional.empty(); // end of feed
        }
        expectObjectStart(token);

        DocumentParseInfo documentParseInfo = new DocumentParseInfo();

        while (true) {
            try {
                token = nextToken(parser);
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
                    bufferFields(parser, documentParseInfo.fieldsBuffer, token);
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
}
