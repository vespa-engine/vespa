// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json.readers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.yahoo.document.DocumentId;
import com.yahoo.document.json.JsonReader;

import java.io.IOException;
import java.util.Optional;

import static com.yahoo.document.json.JsonReader.CREATE_IF_NON_EXISTENT;
import static com.yahoo.document.json.JsonReader.FIELDS;

public class DocumentToFields {
    public static DocumentParseInfo parseToDocumentsFieldsAndInsertFieldsIntoBuffer(JsonParser parser, DocumentId documentId) {
        long indentLevel = 0;
        DocumentParseInfo documentParseInfo = new DocumentParseInfo();
        documentParseInfo.documentId = documentId;
        while (true) {
            // we should now be at the start of a feed operation or at the end of the feed
            JsonToken t = JsonReader.nextToken(parser);
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
                    indentLevel+=10000L;
                    break;
                case END_ARRAY:
                    indentLevel-=10000L;
                    break;
            }
            if (indentLevel == 1 && (t == JsonToken.VALUE_TRUE || t == JsonToken.VALUE_FALSE)) {
                try {
                    if (CREATE_IF_NON_EXISTENT.equals(parser.getCurrentName())) {
                        documentParseInfo.create = Optional.ofNullable(parser.getBooleanValue());
                        continue;
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Got IO exception while parsing document", e);
                }
            }
            if (indentLevel == 2L && t == JsonToken.START_OBJECT) {

                try {
                    if (!FIELDS.equals(parser.getCurrentName())) {
                        continue;
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Got IO exception while parsing document", e);
                }
                JsonReader.bufferFields(parser, documentParseInfo.fieldsBuffer, t);
                break;
            }
        }
        return documentParseInfo;
    }
}
