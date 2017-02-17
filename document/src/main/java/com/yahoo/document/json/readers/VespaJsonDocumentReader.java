package com.yahoo.document.json.readers;

import com.fasterxml.jackson.core.JsonToken;
import com.google.common.base.Preconditions;
import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.Field;
import com.yahoo.document.NumericDataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.fieldpathupdate.AddFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.AssignFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.FieldPathUpdate;
import com.yahoo.document.fieldpathupdate.RemoveFieldPathUpdate;
import com.yahoo.document.json.JsonReaderException;
import com.yahoo.document.json.TokenBuffer;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.document.update.FieldUpdate;

import static com.yahoo.document.json.readers.AddRemoveCreator.createAdds;
import static com.yahoo.document.json.readers.AddRemoveCreator.createRemoves;
import static com.yahoo.document.json.readers.CompositeReader.populateComposite;
import static com.yahoo.document.json.readers.JsonParserHelpers.expectObjectEnd;
import static com.yahoo.document.json.readers.JsonParserHelpers.expectObjectStart;
import static com.yahoo.document.json.readers.MapReader.UPDATE_MATCH;
import static com.yahoo.document.json.readers.MapReader.createMapUpdate;
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
            populateComposite(buffer, put.getDocument());
        } catch (JsonReaderException e) {
            throw JsonReaderException.addDocId(e, put.getId());
        }
    }

    // Exposed for unit testing...
    public void readUpdate(TokenBuffer buffer, DocumentUpdate update) {
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
            FieldPathUpdate.Type fieldPathUpdateType = FieldPathUpdate.Type.valueOf(buffer.currentName().toUpperCase());
            FieldPathUpdate fieldPathUpdate = FieldPathUpdate.create(fieldPathUpdateType, update.getType());

            fieldPathUpdate.setFieldPath(fieldPath);
            DataType dt = fieldPathUpdate.getFieldPath().getResultingDataType();
            if (fieldPathUpdate instanceof AssignFieldPathUpdate) {
                if (dt instanceof NumericDataType) {
                    ((AssignFieldPathUpdate) fieldPathUpdate).setExpression(buffer.currentText());
                } else {
                    FieldValue fv = SingleValueReader.readSingleValue(buffer, dt);
                    ((AssignFieldPathUpdate) fieldPathUpdate).setNewValue(fv);
                }

            } else if (fieldPathUpdate instanceof AddFieldPathUpdate) {
                FieldValue fv = SingleValueReader.readSingleValue(buffer, dt);
                ((AddFieldPathUpdate) fieldPathUpdate).setNewValues((Array) fv);

            } else if (fieldPathUpdate instanceof RemoveFieldPathUpdate) {
                buffer.next();
            }
            update.addFieldPathUpdate(fieldPathUpdate);
            buffer.next();
        }
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
