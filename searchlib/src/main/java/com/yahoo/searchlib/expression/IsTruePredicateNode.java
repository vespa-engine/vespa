// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

import java.util.Objects;
import java.util.Optional;

/**
 * Protocol class for {@code IsTruePredicate}.
 *
 * @author johsol
 */
public class IsTruePredicateNode extends FilterExpressionNode {

    public static final int classId = registerClass(0x4000 + 180, IsTruePredicateNode.class, IsTruePredicateNode::new);

    private ExpressionNode expression;

    public IsTruePredicateNode() {
    }

    public IsTruePredicateNode(ExpressionNode expression) {
        this.expression = expression;
    }

    public Optional<ExpressionNode> getExpression() {
        return Optional.ofNullable(expression);
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    public IsTruePredicateNode clone() {
        return new IsTruePredicateNode(expression != null ? expression.clone() : null);
    }

    @Override
    protected void onSerialize(Serializer buf) {
        serializeOptional(buf, expression);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        expression = (ExpressionNode) deserializeOptional(buf);
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
        IsTruePredicateNode that = (IsTruePredicateNode) o;
        return Objects.equals(expression, that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expression);
    }
}
