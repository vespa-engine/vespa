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
import static com.yahoo.document.json.readers.JsonParserHelpers.expectArrayEnd;
import static com.yahoo.document.json.readers.JsonParserHelpers.expectArrayStart;
import static com.yahoo.document.json.readers.MapReader.UPDATE_MATCH;
import static com.yahoo.document.json.readers.MapReader.createMapUpdate;
import static com.yahoo.document.json.readers.SingleValueReader.readSingleUpdate;

/**
 * @author valerijf
 */
public class VespaJsonDocumentReader {
    private final DocumentType documentType;
    private final DocumentParseInfo documentParseInfo;

    private static final String UPDATE_REMOVE = "remove";
    private static final String UPDATE_ADD = "add";

    public VespaJsonDocumentReader(DocumentType documentType, DocumentParseInfo documentParseInfo) {
        this.documentType = documentType;
        this.documentParseInfo = documentParseInfo;
    }

    public DocumentOperation createDocumentOperation() {
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
                        parseFieldpathsBuffer((DocumentUpdate) documentOperation, documentParseInfo.fieldpathsBuffer);
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
    public static void readUpdate(TokenBuffer buffer, DocumentUpdate update) {
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

    // Exposed for unit testing...
    public static void readPut(TokenBuffer buffer, DocumentPut put) {
        try {
            populateComposite(buffer, put.getDocument());
        } catch (JsonReaderException e) {
            throw JsonReaderException.addDocId(e, put.getId());
        }
    }

    private static void verifyEndState(TokenBuffer buffer, JsonToken expectedFinalToken) {
        Preconditions.checkState(buffer.currentToken() == expectedFinalToken,
                "Expected end of JSON struct (%s), got %s", expectedFinalToken, buffer.currentToken());
        Preconditions.checkState(buffer.nesting() == 0, "Nesting not zero at end of operation");
        Preconditions.checkState(buffer.next() == null, "Dangling data at end of operation");
        Preconditions.checkState(buffer.size() == 0, "Dangling data at end of operation");
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

    private void parseFieldpathsBuffer(DocumentUpdate update, TokenBuffer buffer) {
        expectArrayStart(buffer.currentToken()); // Start of fieldpath operations array
        int localNesting = buffer.nesting();
        JsonToken t = buffer.next();

        while (localNesting <= buffer.nesting()) {
            expectObjectStart(t); // Start of the object inside the array of 'fieldpaths'
            t = buffer.next();
            expectObjectStart(t); // Start of the operation object

            FieldPathUpdate.Type fieldpathType = FieldPathUpdate.Type.valueOf(buffer.currentName().toUpperCase());
            FieldPathUpdate fieldPathUpdate = FieldPathUpdate.create(fieldpathType, documentType);
            readFieldPathUpdate(fieldPathUpdate, buffer);
            update.addFieldPathUpdate(fieldPathUpdate);

            expectObjectEnd(buffer.currentToken()); // End of the operation object
            t = buffer.next();
            expectObjectEnd(t); // End of the object inside the array of 'fieldpaths'
            t = buffer.next();
        }

        expectArrayEnd(t);
    }

    private void readFieldPathUpdate(FieldPathUpdate update, TokenBuffer buffer) {
        expectObjectStart(buffer.currentToken()); // Start of operation object
        int localNesting = buffer.nesting();
        JsonToken t = buffer.next();

        while (localNesting <= buffer.nesting()) {
            if (buffer.currentName().equals("where")) {
                try {
                    update.setWhereClause(buffer.currentText());
                } catch (ParseException e) {
                    throw new RuntimeException("Failed to parse where clause: " + buffer.currentName());
                }
            } else {
                expectObjectStart(t); // Start of fieldpath object
                if (update.getFieldPath() != null) {
                    throw new IllegalArgumentException("Cannot set fieldpath to " + buffer.currentName() + ", is " +
                            "already set to " + update.getOriginalFieldPath());
                }
                update.setFieldPath(buffer.currentName());
                if (update instanceof AssignFieldPathUpdate) {
                    readAssignFieldPath((AssignFieldPathUpdate) update, buffer);

                } else if (update instanceof AddFieldPathUpdate) {
                    readAddFieldPath((AddFieldPathUpdate) update, buffer);

                } else if (update instanceof RemoveFieldPathUpdate) {
                    readRemoveFieldPath((RemoveFieldPathUpdate) update, buffer);
                }
                expectObjectEnd(buffer.currentToken()); // End of fieldpath object
            }
            t = buffer.next();
        }

        expectObjectEnd(t); // End of operation object
    }

    private void readAssignFieldPath(AssignFieldPathUpdate update, TokenBuffer buffer) {
        expectObjectStart(buffer.currentToken()); // Start of fieldpath object
        int localNesting = buffer.nesting();
        JsonToken t = buffer.next();

        while (localNesting <= buffer.nesting()) {
            switch (buffer.currentName()) {
                case "removeifzero":
                    update.setRemoveIfZero(Boolean.parseBoolean(buffer.currentText()));
                    break;

                case "createmissingpath":
                    update.setCreateMissingPath(Boolean.parseBoolean(buffer.currentText()));
                    break;

                case "value":
                    DataType dt = update.getFieldPath().getResultingDataType();

                    if (dt instanceof NumericDataType) {
                        update.setExpression(buffer.currentText());
                    } else {
                        FieldValue fv = CompositeReader.createComposite(buffer, dt);
                        update.setNewValue(fv);
                    }
                    break;

                default:
                    throw new RuntimeException("Unknown attribute for assign fieldpath update: " + buffer.currentName());
            }

            t = buffer.next();
        }

        expectObjectEnd(t); // End of fieldpath object
    }

    private void readAddFieldPath(AddFieldPathUpdate update, TokenBuffer buffer) {
        expectObjectStart(buffer.currentToken()); // Start of fieldpath object
        int localNesting = buffer.nesting();
        JsonToken t = buffer.next();

        while (localNesting <= buffer.nesting()) {
            switch (buffer.currentName()) {
                case "items":
                    DataType dt = update.getFieldPath().getResultingDataType();
                    FieldValue fv = CompositeReader.createComposite(buffer, dt);
                    update.setNewValues((Array) fv);
                    break;

                default:
                    throw new RuntimeException("Unknown attribute for add fieldpath update: " + buffer.currentName());
            }

            t = buffer.next();
        }

        expectObjectEnd(t); // End of fieldpath object
    }

    private void readRemoveFieldPath(RemoveFieldPathUpdate update, TokenBuffer buffer) {
        expectObjectStart(buffer.currentToken()); // Start of fieldpath object
        expectObjectEnd(buffer.next()); // End of fieldpath object
    }
}
