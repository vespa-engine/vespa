package com.yahoo.document.json;

import com.fasterxml.jackson.core.JsonToken;
import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.NumericDataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.fieldpathupdate.AddFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.AssignFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.FieldPathUpdate;
import com.yahoo.document.fieldpathupdate.RemoveFieldPathUpdate;
import com.yahoo.document.json.readers.CompositeReader;
import com.yahoo.document.json.readers.DocumentParseInfo;
import com.yahoo.document.select.parser.ParseException;

import static com.yahoo.document.json.readers.JsonParserHelpers.expectObjectEnd;
import static com.yahoo.document.json.readers.JsonParserHelpers.expectObjectStart;
import static com.yahoo.document.json.readers.JsonParserHelpers.expectArrayEnd;
import static com.yahoo.document.json.readers.JsonParserHelpers.expectArrayStart;

/**
 * @author valerijf
 */
public class VespaJsonDocumentReader {
    private final DocumentType documentType;
    private final DocumentParseInfo documentParseInfo;

    VespaJsonDocumentReader(DocumentType documentType, DocumentParseInfo documentParseInfo) {
        this.documentType = documentType;
        this.documentParseInfo = documentParseInfo;
    }

    public void read(DocumentUpdate update) {
        parseFieldpathsBuffer(update, documentParseInfo.fieldpathsBuffer);
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
                    DataType dt = new ArrayDataType(update.getFieldPath().getResultingDataType());
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
