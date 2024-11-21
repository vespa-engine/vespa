package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.FieldTypeAdapter;
import com.yahoo.vespa.indexinglanguage.expressions.VerificationException;

/**
 * Returns information about the types of fields from a document type.
 *
 * @author bratseth
 */
public class DocumentTypeAdapter implements FieldTypeAdapter {

    private final DocumentType documentType;

    public DocumentTypeAdapter(DocumentType documentType) {
        this.documentType = documentType;
    }

    @Override
    public DataType getInputType(Expression expression, String fieldName) {
        return requireFieldType(expression, fieldName);
    }

    @Override
    public void tryOutputType(Expression expression, String fieldName, DataType valueType) {
        DataType fieldType = requireFieldType(expression, fieldName);
        if ( ! valueType.isAssignableTo(fieldType))
            throw new VerificationException(expression, "Output field '" + fieldName + "' has type " + fieldType.getName() +
                                                        " which is incompatible with " + valueType.getName());
    }

    public DataType requireFieldType(Expression expression, String fieldName) {
        Field field = documentType.getField(fieldName);
        if (field == null)
            throw new VerificationException(expression, "Input field '" + fieldName + "' not found");
        return field.getDataType();
    }

}
