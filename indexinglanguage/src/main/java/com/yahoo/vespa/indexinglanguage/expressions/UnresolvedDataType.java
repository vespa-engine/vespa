// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.PrimitiveDataType;
import com.yahoo.document.datatypes.FieldValue;

/**
 * @author Simon Thoresen Hult
 */
final class UnresolvedDataType extends PrimitiveDataType {

    public static final UnresolvedDataType INSTANCE = new UnresolvedDataType();

    private UnresolvedDataType() {
        super("any", -69, UnresolvedFieldValue.class, UnresolvedFieldValue.getFactory());
    }

    @Override
    public boolean isValueCompatible(FieldValue value) {
        return value != null;
    }
}
