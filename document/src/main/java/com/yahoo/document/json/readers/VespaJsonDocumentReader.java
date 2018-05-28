// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json.readers;

import com.fasterxml.jackson.core.JsonToken;
import com.google.common.base.Preconditions;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.fieldpathupdate.AddFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.AssignFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.FieldPathUpdate;
import com.yahoo.document.fieldpathupdate.RemoveFieldPathUpdate;
import com.yahoo.document.json.JsonReaderException;
import com.yahoo.document.json.TokenBuffer;
import com.yahoo.document.update.FieldUpdate;

import static com.yahoo.document.json.readers.AddRemoveCreator.createAdds;
import static com.yahoo.document.json.readers.AddRemoveCreator.createRemoves;
import static com.yahoo.document.json.readers.CompositeReader.populateComposite;
import static com.yahoo.document.json.readers.JsonParserHelpers.expectObjectEnd;
import static com.yahoo.document.json.readers.JsonParserHelpers.expectObjectStart;
import static com.yahoo.document.json.readers.JsonParserHelpers.expectScalarValue;
import static com.yahoo.document.json.readers.MapReader.UPDATE_MATCH;
import static com.yahoo.document.json.readers.MapReader.createMapUpdate;
import static com.yahoo.document.json.readers.SingleValueReader.UPDATE_ASSIGN;
import static com.yahoo.document.json.readers.SingleValueReader.readSingleUpdate;

/**
 * @author freva
 */
public class VespaJsonDocumentReader {
    private static final String UPDATE_REMOVE = "remove";
    private static final String UPDATE_ADD = "add";

    public DocumentOperation createDocumentOperation(DocumentType documentType, DocumentParseInfo documentParseInfo) {
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
                    readUpdate(documentParseInfo.fieldsBuffer, (DocumentUpdate) documentOperation);
                    verifyEndState(documentParseInfo.fieldsBuffer, JsonToken.END_OBJECT);
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
    public void readPut(TokenBuffer buffer, DocumentPut put) {
        try {
            if (buffer.isEmpty()) // no "fields" map
                throw new IllegalArgumentException(put + " is missing a 'fields' map");
            populateComposite(buffer, put.getDocument());
        } catch (JsonReaderException e) {
            throw JsonReaderException.addDocId(e, put.getId());
        }
    }

    // Exposed for unit testing...
    public void readUpdate(TokenBuffer buffer, DocumentUpdate update) {
        if (buffer.isEmpty())
            throw new IllegalArgumentException("update of document " + update.getId() + " is missing a 'fields' map");
        expectObjectStart(buffer.currentToken());
        int localNesting = buffer.nesting();

        buffer.next();
        while (localNesting <= buffer.nesting()) {
            expectObjectStart(buffer.currentToken());

            String fieldName = buffer.currentName();
            if (isFieldPath(fieldName)) {
                addFieldPathUpdates(update, buffer, fieldName);
            } else {
                addFieldUpdates(update, buffer, fieldName);
            }

            expectObjectEnd(buffer.currentToken());
            buffer.next();
        }
    }

    private void addFieldUpdates(DocumentUpdate update, TokenBuffer buffer, String fieldName) {
        Field field = update.getType().getField(fieldName);
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

    private void addFieldPathUpdates(DocumentUpdate update, TokenBuffer buffer, String fieldPath) {
        int localNesting = buffer.nesting();

        buffer.next();
        while (localNesting <= buffer.nesting()) {
            String fieldPathOperation = buffer.currentName().toLowerCase();
            FieldPathUpdate fieldPathUpdate;
            if (fieldPathOperation.equals(UPDATE_ASSIGN)) {
                fieldPathUpdate = readAssignFieldPathUpdate(update.getType(), fieldPath, buffer);

            } else if (fieldPathOperation.equals(UPDATE_ADD)) {
                fieldPathUpdate = readAddFieldPathUpdate(update.getType(), fieldPath, buffer);

            } else if (fieldPathOperation.equals(UPDATE_REMOVE)) {
                fieldPathUpdate = readRemoveFieldPathUpdate(update.getType(), fieldPath, buffer);

            } else if (SingleValueReader.UPDATE_OPERATION_TO_ARITHMETIC_SIGN.containsKey(fieldPathOperation)) {
                fieldPathUpdate = readArithmeticFieldPathUpdate(update.getType(), fieldPath, buffer, fieldPathOperation);

            } else {
                throw new IllegalArgumentException("Field path update type '" + fieldPathOperation + "' not supported.");
            }
            update.addFieldPathUpdate(fieldPathUpdate);
            buffer.next();
        }
    }

    private AssignFieldPathUpdate readAssignFieldPathUpdate(DocumentType documentType, String fieldPath, TokenBuffer buffer) {
        AssignFieldPathUpdate fieldPathUpdate = new AssignFieldPathUpdate(documentType, fieldPath);
        FieldValue fv = SingleValueReader.readSingleValue(
                buffer, fieldPathUpdate.getFieldPath().getResultingDataType());
        fieldPathUpdate.setNewValue(fv);
        return fieldPathUpdate;
    }

    private AddFieldPathUpdate readAddFieldPathUpdate(DocumentType documentType, String fieldPath, TokenBuffer buffer) {
        AddFieldPathUpdate fieldPathUpdate = new AddFieldPathUpdate(documentType, fieldPath);
        FieldValue fv = SingleValueReader.readSingleValue(
                buffer, fieldPathUpdate.getFieldPath().getResultingDataType());
        fieldPathUpdate.setNewValues((Array) fv);
        return fieldPathUpdate;
    }

    private RemoveFieldPathUpdate readRemoveFieldPathUpdate(DocumentType documentType, String fieldPath, TokenBuffer buffer) {
        expectScalarValue(buffer.currentToken());
        return new RemoveFieldPathUpdate(documentType, fieldPath);
    }

    private AssignFieldPathUpdate readArithmeticFieldPathUpdate(
            DocumentType documentType, String fieldPath, TokenBuffer buffer, String fieldPathOperation) {
        AssignFieldPathUpdate fieldPathUpdate = new AssignFieldPathUpdate(documentType, fieldPath);
        String arithmeticSign = SingleValueReader.UPDATE_OPERATION_TO_ARITHMETIC_SIGN.get(fieldPathOperation);
        double value = Double.valueOf(buffer.currentText());
        String expression = String.format("$value %s %s", arithmeticSign, value);
        fieldPathUpdate.setExpression(expression);
        return fieldPathUpdate;
    }


    private static boolean isFieldPath(String field) {
        return field.matches("^.*?[.\\[\\{].*$");
    }

    private static void verifyEndState(TokenBuffer buffer, JsonToken expectedFinalToken) {
        Preconditions.checkState(buffer.currentToken() == expectedFinalToken,
                "Expected end of JSON struct (%s), got %s", expectedFinalToken, buffer.currentToken());
        Preconditions.checkState(buffer.nesting() == 0, "Nesting not zero at end of operation");
        Preconditions.checkState(buffer.next() == null, "Dangling data at end of operation");
        Preconditions.checkState(buffer.size() == 0, "Dangling data at end of operation");
    }
}
