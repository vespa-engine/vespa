package com.yahoo.document.json;

import com.fasterxml.jackson.core.JsonParser;
import com.google.common.base.Preconditions;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.NumericDataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.fieldpathupdate.AddFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.AssignFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.FieldPathUpdate;
import com.yahoo.document.fieldpathupdate.RemoveFieldPathUpdate;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.document.serialization.DocumentUpdateReader;
import com.yahoo.document.update.FieldUpdate;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author valerijf
 */
public class VespaJsonDocumentReader implements DocumentUpdateReader {
    private final JsonParser parser;
    private final Map<String, Object> operationAttributes = new HashMap<>();

    VespaJsonDocumentReader(JsonParser parser) {
        this.parser = parser;
    }

    @Override
    public void read(DocumentUpdate update) {

    }

    @Override
    public void read(FieldUpdate update) {

    }

    @Override
    public void read(FieldPathUpdate update) {
        assert parser.isExpectedStartObjectToken();

        try {
            parser.nextToken();
            update.setFieldPath(parser.getValueAsString());
            parser.nextToken();

            assert parser.isExpectedStartObjectToken();
            parser.nextToken();
            do {
                String key = parser.getValueAsString();
                Preconditions.checkState(key != null && !key.isEmpty(), "Missing attribute key for operation");
                parser.nextToken();

                System.out.println(update.getFieldPath().getResultingDataType());
                if ("where".equals(key)) {
                    update.setWhereClause(parser.getValueAsString());
                } else {
                    Object value = null;
                    if ("value".equals(key)) {
                        value = parser.getEmbeddedObject();
                    } else if (parser.currentToken().isBoolean()) {
                        value = parser.getBooleanValue();
                    } else if (parser.currentToken().isNumeric()) {
                        value = parser.getNumberValue();
                    } else if (parser.currentToken().isScalarValue()) { // Non-structured value
                        value = parser.getValueAsString();
                    }

                    operationAttributes.put(key, value);
                }
            } while (! parser.nextToken().isStructEnd());
        } catch (IOException | ParseException e) {
            throw new RuntimeException("Failed to parse JSON", e);
        }
    }


    @Override
    public void read(AssignFieldPathUpdate update) {
        Optional.ofNullable((Boolean) operationAttributes.get("removeifzero"))
                .ifPresent(update::setRemoveIfZero);
        Optional.ofNullable((Boolean) operationAttributes.get("createmissingpath"))
                .ifPresent(update::setCreateMissingPath);

        DataType dt = update.getFieldPath().getResultingDataType();
        if (dt instanceof NumericDataType) {
            update.setExpression(String.valueOf(operationAttributes.get("value")));
        } else {
            FieldValue fv = dt.createFieldValue();
            fv.assign(operationAttributes.get("value"));
            update.setNewValue(fv);
        }

    }

    @Override
    public void read(AddFieldPathUpdate update) {

    }

    @Override
    public void read(RemoveFieldPathUpdate update) {

    }

    @Override
    public DocumentId readDocumentId() {
        return null;
    }

    @Override
    public DocumentType readDocumentType() {
        return null;
    }
}
