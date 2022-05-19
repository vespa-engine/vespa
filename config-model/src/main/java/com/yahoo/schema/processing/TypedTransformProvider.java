// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.Attribute;
import com.yahoo.vespa.indexinglanguage.ValueTransformProvider;
import com.yahoo.vespa.indexinglanguage.expressions.*;

/**
 * @author Simon Thoresen Hult
 */
public abstract class TypedTransformProvider extends ValueTransformProvider {

    private final Schema schema;
    private DataType fieldType;

    TypedTransformProvider(Class<? extends Expression> transformClass, Schema schema) {
        super(transformClass);
        this.schema = schema;
    }

    @Override
    protected final boolean requiresTransform(Expression exp) {
        if (exp instanceof OutputExpression) {
            String fieldName = ((OutputExpression)exp).getFieldName();
            if (exp instanceof AttributeExpression) {
                Attribute attribute = schema.getAttribute(fieldName);
                if (attribute == null)
                    throw new IllegalArgumentException("Attribute '" + fieldName + "' not found.");
                fieldType = attribute.getDataType();
            }
            else if (exp instanceof IndexExpression) {
                Field field = schema.getConcreteField(fieldName);
                if (field == null)
                    throw new IllegalArgumentException("Index field '" + fieldName + "' not found.");
                fieldType = field.getDataType();
            }
            else if (exp instanceof SummaryExpression) {
                Field field = schema.getSummaryField(fieldName);
                if (field == null)
                    throw new IllegalArgumentException("Summary field '" + fieldName + "' not found.");
                fieldType = field.getDataType();
            }
            else {
                throw new UnsupportedOperationException();
            }
        }
        return requiresTransform(exp, fieldType);
    }

    @Override
    protected final Expression newTransform() {
        return newTransform(fieldType);
    }

    protected abstract boolean requiresTransform(Expression exp, DataType fieldType);

    protected abstract Expression newTransform(DataType fieldType);

}
