// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

import java.util.Objects;
import java.util.Optional;

/**
 * Protocol class for {@code RegexPredicate}.
 *
 * @author bjorncs
 */
public class RegexPredicateNode extends FilterExpressionNode {

    public static final int classId = registerClass(0x4000 + 172, RegexPredicateNode.class, RegexPredicateNode::new);

    private String pattern;
    private ExpressionNode expression;

    public RegexPredicateNode() {}

    public RegexPredicateNode(String pattern, ExpressionNode expression) {
        this.pattern = Objects.requireNonNull(pattern, "pattern cannot be null");
        this.expression = expression;
    }

    public String getPattern() { return pattern; }
    public Optional<ExpressionNode> getExpression() { return Optional.ofNullable(expression); }

    @Override protected int onGetClassId() { return classId; }

    @Override
    public RegexPredicateNode clone() {
        return new RegexPredicateNode(pattern, expression != null ? expression.clone() : null);
    }

    @Override
    protected void onSerialize(Serializer buf) {
        putUtf8(buf, pattern);
        // Future-proofing: allow optional expression in protocol in case it's added later to the grouping language
        serializeOptional(buf, expression);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        pattern = getUtf8(buf);
        expression = (ExpressionNode)deserializeOptional(buf);
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("pattern", pattern);
        visitor.visit("expression", expression);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegexPredicateNode that = (RegexPredicateNode) o;
        return Objects.equals(pattern, that.pattern) && Objects.equals(expression, that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pattern, expression);
    }
}
