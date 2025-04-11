// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.Identifiable;
import com.yahoo.vespa.objects.Serializer;

/**
 * Base class for a filter expression used by {@link com.yahoo.searchlib.aggregation.GroupingLevel}.
 * Protocol class for {@code FilterExpression}.
 *
 * @author bjorncs
 */
public abstract class FilterExpressionNode extends Identifiable {
    // No registerClass() call here as class is abstract

    // Force subclasses to override methods from `Identifiable`
    protected abstract int onGetClassId();
    public abstract FilterExpressionNode clone();
    protected abstract void onSerialize(Serializer buf);
    protected abstract void onDeserialize(Deserializer buf);
    public abstract int hashCode();
    public abstract boolean equals(Object obj);
    // Not overriding visitMembers() as abstract as subclass must be able to invoke super.visitMembers()

    // Ensure subclasses cannot override methods from `Identifiable`
    @Override public final String toString() { return super.toString(); }
}
