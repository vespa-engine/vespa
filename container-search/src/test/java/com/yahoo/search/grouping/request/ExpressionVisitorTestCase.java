// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class ExpressionVisitorTestCase {

    @Test
    void requireThatExpressionsAreVisited() {
        GroupingOperation op = new AllOperation();

        final List<GroupingExpression> lst = new LinkedList<>();
        GroupingExpression exp = new AttributeValue("groupBy");
        op.setGroupBy(exp);
        lst.add(exp);

        op.addOrderBy(exp = new AttributeValue("orderBy1"));
        lst.add(exp);
        op.addOrderBy(exp = new AttributeValue("orderBy1"));
        lst.add(exp);

        op.addOutput(exp = new AttributeValue("output1"));
        lst.add(exp);
        op.addOutput(exp = new AttributeValue("output2"));
        lst.add(exp);

        op.visitExpressions(exp1 -> assertNotNull(lst.remove(exp1)));
        assertTrue(lst.isEmpty());
    }

    @Test
    void requireThatChildOperationsAreVisited() {
        GroupingOperation root, parentA, childA1, childA2, parentB, childB1;
        root = new AllOperation()
                .addChild(parentA = new AllOperation()
                        .addChild(childA1 = new AllOperation())
                        .addChild(childA2 = new AllOperation()))
                .addChild(parentB = new AllOperation()
                        .addChild(childB1 = new AllOperation()));

        final List<GroupingExpression> lst = new LinkedList<>();
        GroupingExpression exp = new AttributeValue("parentA");
        parentA.setGroupBy(exp);
        lst.add(exp);

        childA1.setGroupBy(exp = new AttributeValue("childA1"));
        lst.add(exp);

        childA2.setGroupBy(exp = new AttributeValue("childA2"));
        lst.add(exp);

        parentB.setGroupBy(exp = new AttributeValue("parentB"));
        lst.add(exp);

        childB1.setGroupBy(exp = new AttributeValue("childB1"));
        lst.add(exp);

        root.visitExpressions(exp1 -> assertNotNull(lst.remove(exp1)));
        assertTrue(lst.isEmpty());
    }

    @Test
    void requireThatExpressionsArgumentsAreVisited() {
        final List<GroupingExpression> lst = new LinkedList<>();
        GroupingExpression arg1 = new AttributeValue("arg1");
        lst.add(arg1);
        GroupingExpression arg2 = new AttributeValue("arg2");
        lst.add(arg2);

        new AndFunction(arg1, arg2).visit(exp -> assertNotNull(lst.remove(exp)));
        assertTrue(lst.isEmpty());
    }
}
