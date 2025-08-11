// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

import java.util.Objects;
import java.util.Optional;

/**
 * Protocol class for {@code RangePredicate}.
 *
 * @author johsol
 */
public class RangePredicateNode extends FilterExpressionNode {

    public static final int classId = registerClass(0x4000 + 178, RangePredicateNode.class, RangePredicateNode::new);

    private Number lower;
    private Number upper;
    private boolean lowerInclusive;
    private boolean upperInclusive;
    private ExpressionNode expression;

    public RangePredicateNode() {}

    public RangePredicateNode(Number lower, Number upper, boolean lowerInclusive, boolean upperInclusive, ExpressionNode expression) {
        this.lower = lower;
        this.upper = upper;
        this.lowerInclusive = lowerInclusive;
        this.upperInclusive = upperInclusive;
        this.expression = expression;
    }
    public Number getLower() { return lower; }
    public Number getUpper() { return upper; }
    public boolean getLowerInclusive() { return lowerInclusive; }
    public boolean getUpperInclusive() { return upperInclusive; }
    public Optional<ExpressionNode> getExpression() { return Optional.ofNullable(expression); }


    @Override protected int onGetClassId() { return classId; }

    @Override
    public RangePredicateNode clone() {
        return new RangePredicateNode(lower, upper, lowerInclusive, upperInclusive, expression != null ? expression.clone() : null);
    }

    @Override
    protected void onSerialize(Serializer buf) {
        buf.putDouble(null, lower.doubleValue());
        buf.putDouble(null, upper.doubleValue());
        buf.putByte(null, (byte)(lowerInclusive ? 1 : 0) );
        buf.putByte(null, (byte)(upperInclusive ? 1 : 0) );
        serializeOptional(buf, expression);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        lower = buf.getDouble(null);
        upper = buf.getDouble(null);
        lowerInclusive = buf.getByte(null) != 0;
        upperInclusive = buf.getByte(null) != 0;
        expression = (ExpressionNode)deserializeOptional(buf);
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("lower", lower);
        visitor.visit("upper", upper);
        visitor.visit("lowerInclusive", lowerInclusive);
        visitor.visit("upperInclusive", upperInclusive);
        visitor.visit("expression", expression);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RangePredicateNode that = (RangePredicateNode) o;
        return Objects.equals(lower, that.lower) &&
                Objects.equals(upper, that.upper) &&
                Objects.equals(lowerInclusive, that.lowerInclusive) &&
                Objects.equals(upperInclusive, that.upperInclusive) &&
                Objects.equals(expression, that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lower, upper, lowerInclusive, upperInclusive, expression);
    }
}
