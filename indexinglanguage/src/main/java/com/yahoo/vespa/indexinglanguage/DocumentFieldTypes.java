package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.FieldTypes;
import com.yahoo.vespa.indexinglanguage.expressions.VerificationException;

/**
 * Returns information about the types of fields from a document type.
 *
 * @author bratseth
 */
public class DocumentFieldTypes implements FieldTypes {

    private final DocumentType documentType;

    public DocumentFieldTypes(DocumentType documentType) {
        this.documentType = documentType;
    }

    @Override
    public DataType getFieldType(String fieldName, Expression expression) {
        return requireFieldType(expression, fieldName);
    }

    public DataType requireFieldType(Expression expression, String fieldName) {
        Field field = documentType.getField(fieldName);
        if (field == null)
            throw new VerificationException(expression, "Input field '" + fieldName + "' not found");
        return field.getDataType();
    }

}
