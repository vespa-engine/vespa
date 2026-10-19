// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Protocol representation of a correlated grouping filter. */
public class CorrelatedPredicateNode extends FilterExpressionNode {
    public static final int classId = registerClass(0x4000 + 185, CorrelatedPredicateNode.class, CorrelatedPredicateNode::new);
    private String label;
    private List<String> variables;
    private List<ExpressionNode> fields;

    public CorrelatedPredicateNode() {}

    public CorrelatedPredicateNode(String label, List<String> variables, List<ExpressionNode> fields) {
        if (label.isBlank() || variables.isEmpty() || variables.size() != fields.size() ||
            variables.stream().anyMatch(String::isBlank) ||
            variables.stream().distinct().count() != variables.size() ||
            fields.stream().anyMatch(field -> !(field instanceof AttributeNode)))
            throw new IllegalArgumentException("correlated requires a label and distinct variable/attribute pairs");
        this.label = label;
        this.variables = List.copyOf(variables);
        this.fields = List.copyOf(fields);
    }

    public String getLabel() { return label; }
    public List<String> getVariables() { return variables; }
    public List<ExpressionNode> getFields() { return fields; }
    @Override protected int onGetClassId() { return classId; }

    @Override public CorrelatedPredicateNode clone() {
        return new CorrelatedPredicateNode(label, variables, fields.stream().map(ExpressionNode::clone).toList());
    }

    @Override protected void onSerialize(Serializer buf) {
        putUtf8(buf, label);
        buf.putInt(null, fields.size());
        for (int i = 0; i < fields.size(); ++i) {
            putUtf8(buf, variables.get(i));
            serializeOptional(buf, fields.get(i));
        }
    }

    @Override protected void onDeserialize(Deserializer buf) {
        label = getUtf8(buf);
        int count = buf.getInt(null);
        if (count <= 0) throw new IllegalArgumentException("correlated requires variable/attribute pairs");
        var names = new ArrayList<String>();
        var attributes = new ArrayList<ExpressionNode>();
        for (int i = 0; i < count; ++i) {
            names.add(getUtf8(buf));
            attributes.add((ExpressionNode) deserializeOptional(buf));
        }
        var validated = new CorrelatedPredicateNode(label, names, attributes);
        variables = validated.variables;
        fields = validated.fields;
    }

    @Override public void visitMembers(ObjectVisitor visitor) {
        visitor.visit("label", label);
        visitor.visit("variables", variables);
        visitor.visit("fields", fields);
    }
    @Override public int hashCode() { return Objects.hash(label, variables, fields); }
    @Override public boolean equals(Object other) {
        return other instanceof CorrelatedPredicateNode that && label.equals(that.label) &&
               variables.equals(that.variables) && fields.equals(that.fields);
    }
}
