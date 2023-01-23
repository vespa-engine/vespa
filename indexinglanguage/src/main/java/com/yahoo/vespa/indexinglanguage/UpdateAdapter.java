// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.DocumentUpdate;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.FieldValueAdapter;

/**
 * @author Simon Thoresen Hult
 */
public interface UpdateAdapter extends FieldValueAdapter {

    DocumentUpdate getOutput();
    Expression getExpression(Expression expression);

    @Override
    default boolean isComplete() { return false; }

}
