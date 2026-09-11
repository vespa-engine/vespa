// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Protocol class for {@code InPredicate}.
 *
 * @author johsol
 */
public class InPredicateNode extends FilterExpressionNode {

    public static final int classId = registerClass(0x4000 + 184, InPredicateNode.class, InPredicateNode::new);

    private ExpressionNode expression;
    private List<ExpressionNode> args = new ArrayList<>();

    public InPredicateNode() {}

    public InPredicateNode(ExpressionNode expression, List<ExpressionNode> args) {
        this.expression = expression;
        this.args = args;
    }

    public Optional<ExpressionNode> getExpression() {
        return Optional.ofNullable(expression);
    }

    public List<ExpressionNode> getArgs() { return args; }

    @Override protected int onGetClassId() { return classId; }

    @Override
    public InPredicateNode clone() {
        return new InPredicateNode(expression != null ? expression.clone() : null,
                                   args.stream().map(ExpressionNode::clone).toList());
    }

    @Override
    protected void onSerialize(Serializer buf) {
        serializeOptional(buf, expression);
        buf.putInt(null, args.size());
        for (ExpressionNode node : args) {
            serializeOptional(buf, node);
        }
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        expression = (ExpressionNode)deserializeOptional(buf);
        args = new ArrayList<>();
        int numArgs = buf.getInt(null);
        for (int i = 0; i < numArgs; i++) {
            args.add((ExpressionNode)deserializeOptional(buf));
        }
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("expression", expression);
        visitor.visit("args", args);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        InPredicateNode that = (InPredicateNode) o;
        return Objects.equals(expression, that.expression) && Objects.equals(args, that.args);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expression, args);
    }
}
