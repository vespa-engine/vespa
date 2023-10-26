// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

class ProjectionBuilder {

    private final Map<String, OperatorNode<ExpressionOperator>> fields = Maps.newLinkedHashMap();
    private final Set<String> aliasNames = Sets.newHashSet();

    public void addField(String name, OperatorNode<ExpressionOperator> expr) {
        String aliasName = name;
        if (name == null) {
            name = assignName(expr);
        }
        if (fields.containsKey(name)) {
            throw new ProgramCompileException(expr.getLocation(), "Field alias '%s' already defined", name);
        }
        fields.put(name, expr);
        if (aliasName != null) {
            // Store use
            aliasNames.add(aliasName);
        }
    }

    public boolean isAlias(String name) {
        return aliasNames.contains(name);
    }

    private String assignName(OperatorNode<ExpressionOperator> expr) {
        String baseName = switch (expr.getOperator()) {
            case PROPREF -> (String) expr.getArgument(1);
            case READ_RECORD -> (String) expr.getArgument(0);
            case READ_FIELD -> (String) expr.getArgument(1);
            case VARREF -> (String) expr.getArgument(0);
            default -> "expr";
            // fall through, leaving baseName alone
        };
        int c = 0;
        String candidate = baseName;
        while (fields.containsKey(candidate)) {
            candidate = baseName + (++c);
        }
        return candidate;
    }

    public OperatorNode<SequenceOperator> make(OperatorNode<SequenceOperator> target) {
        List<OperatorNode<ProjectOperator>> lst = new ArrayList<>();
        for (Map.Entry<String, OperatorNode<ExpressionOperator>> e : fields.entrySet()) {
            if (e.getKey().startsWith("*")) {
                lst.add(OperatorNode.create(ProjectOperator.MERGE_RECORD, e.getValue().getArgument(0)));
            } else if (e.getValue().getOperator() == ExpressionOperator.READ_RECORD) {
                lst.add(OperatorNode.create(ProjectOperator.RECORD, e.getValue(), e.getKey()));
            } else {
                lst.add(OperatorNode.create(ProjectOperator.FIELD, e.getValue(), e.getKey()));
            }
        }
        return OperatorNode.create(SequenceOperator.PROJECT, target, List.copyOf(lst));
    }

}
