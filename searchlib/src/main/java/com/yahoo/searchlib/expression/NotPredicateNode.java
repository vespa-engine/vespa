// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

import java.util.Objects;
import java.util.Optional;

/**
 * Protocol class for {@code NotPredicate}.
 *
 * @author johsol
 */
public class NotPredicateNode extends FilterExpressionNode {

    public static final int classId = registerClass(0x4000 + 174, NotPredicateNode.class, NotPredicateNode::new);

    private FilterExpressionNode expression;

    public NotPredicateNode() {}

    public NotPredicateNode(FilterExpressionNode expression) {
        this.expression = expression;
    }

    public Optional<FilterExpressionNode> getExpression() { return Optional.ofNullable(expression); }

    @Override protected int onGetClassId() { return classId; }

    @Override
    public NotPredicateNode clone() {
        return new NotPredicateNode(expression != null ? expression.clone() : null);
    }

    @Override
    protected void onSerialize(Serializer buf) {
        // Future-proofing: allow optional expression in protocol in case it's added later to the grouping language
        serializeOptional(buf, expression);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        expression = (FilterExpressionNode)deserializeOptional(buf);
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("expression", expression);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NotPredicateNode that = (NotPredicateNode) o;
        return Objects.equals(expression, that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expression);
    }
}
