// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DocumentType;
import com.yahoo.vespa.objects.ObjectOperation;
import com.yahoo.vespa.objects.ObjectPredicate;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Simon Thoresen Hult
 */
public abstract class ExpressionList<T extends Expression> extends CompositeExpression implements Iterable<T> {

    private final List<T> expressions = new LinkedList<T>();

    protected ExpressionList() {
        // empty
    }

    protected ExpressionList(Iterable<? extends T> lst) {
        for (T exp : lst) {
            this.expressions.add(exp);
        }
    }

    public int size() {
        return expressions.size();
    }

    public T get(int idx) {
        return expressions.get(idx);
    }

    public boolean isEmpty() {
        return expressions.isEmpty();
    }

    public List<T> asList() {
        return Collections.unmodifiableList(expressions);
    }

    @Override
    public Iterator<T> iterator() {
        return expressions.iterator();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public boolean equals(Object obj) {
        if (!(obj instanceof ExpressionList)) {
            return false;
        }
        ExpressionList rhs = (ExpressionList)obj;
        if (!expressions.equals(rhs.expressions)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + expressions.hashCode();
    }

    @Override
    public void selectMembers(ObjectPredicate predicate, ObjectOperation operation) {
        for (T exp : expressions) {
            exp.select(predicate, operation);
        }
    }
}
