// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.vespa.indexinglanguage.ValueTransformProvider;
import com.yahoo.vespa.indexinglanguage.expressions.*;

/**
 * @author Simon Thoresen
 */
public abstract class TypedTransformProvider extends ValueTransformProvider {

    private final Search search;
    private DataType fieldType;

    TypedTransformProvider(Class<? extends Expression> transformClass, Search search) {
        super(transformClass);
        this.search = search;
    }

    @Override
    protected final boolean requiresTransform(Expression exp) {
        if (exp instanceof OutputExpression) {
            String fieldName = ((OutputExpression)exp).getFieldName();
            if (exp instanceof AttributeExpression) {
                Attribute attribute = search.getAttribute(fieldName);
                if (attribute == null)
                    throw new IllegalArgumentException("Attribute '" + fieldName + "' not found.");
                fieldType = attribute.getDataType();
            }
            else if (exp instanceof IndexExpression) {
                Field field = search.getConcreteField(fieldName);
                if (field == null)
                    throw new IllegalArgumentException("Index field '" + fieldName + "' not found.");
                fieldType = field.getDataType();
            }
            else if (exp instanceof SummaryExpression) {
                Field field = search.getSummaryField(fieldName);
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
