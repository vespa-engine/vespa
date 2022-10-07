// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import com.yahoo.document.json.ParsedDocumentOperation;
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
import static com.yahoo.document.json.readers.TensorAddUpdateReader.createTensorAddUpdate;
import static com.yahoo.document.json.readers.TensorAddUpdateReader.isTensorField;
import static com.yahoo.document.json.readers.TensorModifyUpdateReader.UPDATE_MODIFY;
import static com.yahoo.document.json.readers.TensorModifyUpdateReader.createModifyUpdate;
import static com.yahoo.document.json.readers.TensorRemoveUpdateReader.createTensorRemoveUpdate;

/**
 * @author freva
 */
public class VespaJsonDocumentReader {

    private static final String UPDATE_REMOVE = "remove";
    private static final String UPDATE_ADD = "add";

    private final boolean ignoreUndefinedFields;

    public VespaJsonDocumentReader(boolean ignoreUndefinedFields) {
        this.ignoreUndefinedFields = ignoreUndefinedFields;
    }

    public ParsedDocumentOperation createDocumentOperation(DocumentType documentType, DocumentParseInfo documentParseInfo) {
        final DocumentOperation documentOperation;
        boolean fullyApplied = true;
        try {
            switch (documentParseInfo.operationType) {
                case PUT -> {
                    documentOperation = new DocumentPut(new Document(documentType, documentParseInfo.documentId));
                    fullyApplied = readPut(documentParseInfo.fieldsBuffer, (DocumentPut) documentOperation);
                    verifyEndState(documentParseInfo.fieldsBuffer, JsonToken.END_OBJECT);
                }
                case REMOVE -> documentOperation = new DocumentRemove(documentParseInfo.documentId);
                case UPDATE -> {
                    documentOperation = new DocumentUpdate(documentType, documentParseInfo.documentId);
                    fullyApplied = readUpdate(documentParseInfo.fieldsBuffer, (DocumentUpdate) documentOperation);
                    verifyEndState(documentParseInfo.fieldsBuffer, JsonToken.END_OBJECT);
                }
                default -> throw new IllegalStateException("Implementation out of sync with itself. This is a bug.");
            }
        } catch (JsonReaderException e) {
            throw JsonReaderException.addDocId(e, documentParseInfo.documentId);
        }
        if (documentParseInfo.create.isPresent()) {
            if (! (documentOperation instanceof DocumentUpdate update)) {
                throw new IllegalArgumentException("Could not set create flag on non update operation.");
            }
            update.setCreateIfNonExistent(documentParseInfo.create.get());
        }
        return new ParsedDocumentOperation(documentOperation, fullyApplied);
    }

    // Exposed for unit testing...
    public boolean readPut(TokenBuffer buffer, DocumentPut put) {
        try {
            if (buffer.isEmpty()) // no "fields" map
                throw new IllegalArgumentException(put + " is missing a 'fields' map");
            return populateComposite(buffer, put.getDocument(), ignoreUndefinedFields);
        } catch (JsonReaderException e) {
            throw JsonReaderException.addDocId(e, put.getId());
        }
    }

    // Exposed for unit testing...
    public boolean readUpdate(TokenBuffer buffer, DocumentUpdate update) {
        if (buffer.isEmpty())
            throw new IllegalArgumentException("Update of document " + update.getId() + " is missing a 'fields' map");
        expectObjectStart(buffer.currentToken());
        int localNesting = buffer.nesting();

        buffer.next();
        boolean fullyApplied = true;
        while (localNesting <= buffer.nesting()) {
            expectObjectStart(buffer.currentToken());

            String fieldName = buffer.currentName();
            try {
                if (isFieldPath(fieldName)) {
                    fullyApplied &= addFieldPathUpdates(update, buffer, fieldName);
                } else {
                    fullyApplied &= addFieldUpdates(update, buffer, fieldName);
                }
                expectObjectEnd(buffer.currentToken());
            }
            catch (IllegalArgumentException | IndexOutOfBoundsException e) {
                throw new IllegalArgumentException("Error in '" + fieldName + "'", e);
            }
            buffer.next();
        }
        return fullyApplied;
    }

    private boolean addFieldUpdates(DocumentUpdate update, TokenBuffer buffer, String fieldName) {
        Field field = update.getType().getField(fieldName);
        if (field == null) {
            if (! ignoreUndefinedFields)
                throw new IllegalArgumentException("No field named '" + fieldName + "' in " + update.getType());
            buffer.skipToRelativeNesting(-1);
            return false;
        }

        int localNesting = buffer.nesting();
        FieldUpdate fieldUpdate = FieldUpdate.create(field);

        buffer.next();
        while (localNesting <= buffer.nesting()) {
            switch (buffer.currentName()) {
                case UPDATE_REMOVE:
                    if (isTensorField(field)) {
                        fieldUpdate.addValueUpdate(createTensorRemoveUpdate(buffer, field));
                    } else {
                        createRemoves(buffer, field, fieldUpdate, ignoreUndefinedFields);
                    }
                    break;
                case UPDATE_ADD:
                    if (isTensorField(field)) {
                        fieldUpdate.addValueUpdate(createTensorAddUpdate(buffer, field));
                    } else {
                        createAdds(buffer, field, fieldUpdate, ignoreUndefinedFields);
                    }
                    break;
                case UPDATE_MATCH:
                    fieldUpdate.addValueUpdate(createMapUpdate(buffer, field, ignoreUndefinedFields));
                    break;
                case UPDATE_MODIFY:
                    fieldUpdate.addValueUpdate(createModifyUpdate(buffer, field));
                    break;
                default:
                    String action = buffer.currentName();
                    fieldUpdate.addValueUpdate(readSingleUpdate(buffer, field.getDataType(), action, ignoreUndefinedFields));
            }
            buffer.next();
        }
        update.addFieldUpdate(fieldUpdate);
        return true;
    }

    private boolean addFieldPathUpdates(DocumentUpdate update, TokenBuffer buffer, String fieldPath) {
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
        return true; // TODO: Track fullyApplied for fieldPath updates
    }

    private AssignFieldPathUpdate readAssignFieldPathUpdate(DocumentType documentType, String fieldPath, TokenBuffer buffer) {
        AssignFieldPathUpdate fieldPathUpdate = new AssignFieldPathUpdate(documentType, fieldPath);
        FieldValue fv = SingleValueReader.readSingleValue(buffer, fieldPathUpdate.getFieldPath().getResultingDataType(),
                                                          ignoreUndefinedFields);
        fieldPathUpdate.setNewValue(fv);
        return fieldPathUpdate;
    }

    private AddFieldPathUpdate readAddFieldPathUpdate(DocumentType documentType, String fieldPath, TokenBuffer buffer) {
        AddFieldPathUpdate fieldPathUpdate = new AddFieldPathUpdate(documentType, fieldPath);
        FieldValue fv = SingleValueReader.readSingleValue(buffer, fieldPathUpdate.getFieldPath().getResultingDataType(),
                                                          ignoreUndefinedFields);
        fieldPathUpdate.setNewValues((Array) fv);
        return fieldPathUpdate;
    }

    private RemoveFieldPathUpdate readRemoveFieldPathUpdate(DocumentType documentType, String fieldPath, TokenBuffer buffer) {
        expectScalarValue(buffer.currentToken());
        return new RemoveFieldPathUpdate(documentType, fieldPath);
    }

    private AssignFieldPathUpdate readArithmeticFieldPathUpdate(DocumentType documentType, String fieldPath,
                                                                TokenBuffer buffer, String fieldPathOperation) {
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
