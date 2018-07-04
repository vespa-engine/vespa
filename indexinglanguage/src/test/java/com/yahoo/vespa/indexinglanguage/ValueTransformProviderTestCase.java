// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.collections.Pair;
import com.yahoo.vespa.indexinglanguage.expressions.*;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class ValueTransformProviderTestCase {

    @Test
    public void requireThatInsertWorks() {
        assertProvided(new StatementExpression(new IndexExpression("foo")),
                       new StatementExpression(new LowerCaseExpression(),
                                               new IndexExpression("foo")));
    }

    @Test
    public void requireThatInsertionIsJustInTime() {
        assertProvided(new StatementExpression(new TrimExpression(),
                                               new IndexExpression("foo")),
                       new StatementExpression(new TrimExpression(),
                                               new LowerCaseExpression(),
                                               new IndexExpression("foo")));
    }

    @Test
    public void requireThatExistingTransformIsDetected() {
        assertProvided(new StatementExpression(new LowerCaseExpression(),
                                               new IndexExpression("foo")),
                       new StatementExpression(new LowerCaseExpression(),
                                               new IndexExpression("foo")));
    }

    @Test
    public void requireThatExistingRedundantTransformIsRemoved() {
        assertProvided(new StatementExpression(new IndexExpression("foo"),
                                               new LowerCaseExpression(),
                                               new IndexExpression("bar")),
                       new StatementExpression(new LowerCaseExpression(),
                                               new IndexExpression("foo"),
                                               new IndexExpression("bar")));
    }

    @Test
    public void requireThatNoRedundantTransformIsInserted() {
        assertProvided(new StatementExpression(new IndexExpression("foo"),
                                               new IndexExpression("bar")),
                       new StatementExpression(new LowerCaseExpression(),
                                               new IndexExpression("foo"),
                                               new IndexExpression("bar")));
    }

    @Test
    public void requireThatExistingTransformIsPreserved() {
        assertProvided(new StatementExpression(new LowerCaseExpression(),
                                               new IndexExpression("foo"),
                                               new IndexExpression("bar")),
                       new StatementExpression(new LowerCaseExpression(),
                                               new IndexExpression("foo"),
                                               new IndexExpression("bar")));
    }

    @Test
    public void requireThatCompositeInsertWorks() {
        assertProvided(new StatementExpression(new ForEachExpression(new IndexExpression("foo"))),
                       new StatementExpression(new ForEachExpression(new StatementExpression(
                               new LowerCaseExpression(),
                               new IndexExpression("foo")))));
    }

    @Test
    public void requireThatStatementsAreManagedSeparately() {
        assertProvided(new ScriptExpression(new StatementExpression(new IndexExpression("foo")),
                                            new StatementExpression(new IndexExpression("bar"))),
                       new ScriptExpression(new StatementExpression(new LowerCaseExpression(),
                                                                    new IndexExpression("foo")),
                                            new StatementExpression(new LowerCaseExpression(),
                                                                    new IndexExpression("bar"))));
    }

    @Test
    public void requireThatIfThenBranchesAreManagedSeparately() {
        assertProvided(new StatementExpression(new IfThenExpression(new IndexExpression("a"),
                                                                    IfThenExpression.Comparator.EQ,
                                                                    new IndexExpression("b"),
                                                                    new IndexExpression("c"),
                                                                    new IndexExpression("d")),
                                               new IndexExpression("e")),
                       new StatementExpression(new IfThenExpression(new StatementExpression(new LowerCaseExpression(),
                                                                                            new IndexExpression("a")),
                                                                    IfThenExpression.Comparator.EQ,
                                                                    new StatementExpression(new LowerCaseExpression(),
                                                                                            new IndexExpression("b")),
                                                                    new StatementExpression(new LowerCaseExpression(),
                                                                                            new IndexExpression("c")),
                                                                    new StatementExpression(new LowerCaseExpression(),
                                                                                            new IndexExpression("d"))),
                                               new LowerCaseExpression(),
                                               new IndexExpression("e")));
    }

    @Test
    public void requireThatSelectInputBranchesAreManagedSeparately() {
        List<Pair<String, Expression>> before = new LinkedList<Pair<String, Expression>>();
        before.add(new Pair<String, Expression>("a", new IndexExpression("b")));
        before.add(new Pair<String, Expression>("c", new IndexExpression("d")));

        List<Pair<String, Expression>> after = new LinkedList<Pair<String, Expression>>();
        after.add(new Pair<String, Expression>("a", new StatementExpression(new LowerCaseExpression(),
                                                                            new IndexExpression("b"))));
        after.add(new Pair<String, Expression>("c", new StatementExpression(new LowerCaseExpression(),
                                                                            new IndexExpression("d"))));

        assertProvided(new StatementExpression(new SelectInputExpression(before),
                                               new IndexExpression("e")),
                       new StatementExpression(new SelectInputExpression(after),
                                               new LowerCaseExpression(),
                                               new IndexExpression("e")));
    }

    @Test
    public void requireThatSwitchBranchesAreManagedSeparately() {
        Map<String, Expression> before = new LinkedHashMap<String, Expression>();
        before.put("a", new IndexExpression("b"));
        before.put("c", new IndexExpression("d"));

        Map<String, Expression> after = new LinkedHashMap<String, Expression>();
        after.put("a", new StatementExpression(new LowerCaseExpression(),
                                               new IndexExpression("b")));
        after.put("c", new StatementExpression(new LowerCaseExpression(),
                                               new IndexExpression("d")));

        assertProvided(new StatementExpression(new SwitchExpression(before,
                                                                    new IndexExpression("e")),
                                               new IndexExpression("f")),
                       new StatementExpression(new SwitchExpression(after,
                                                                    new StatementExpression(new LowerCaseExpression(),
                                                                                            new IndexExpression("e"))),
                                               new LowerCaseExpression(),
                                               new IndexExpression("f")));
    }

    private void assertProvided(Expression before, Expression after) {
        assertEquals(after, new MyProvider().convert(before));
    }

    private static class MyProvider extends ValueTransformProvider {

        MyProvider() {
            super(LowerCaseExpression.class);
        }

        @Override
        protected boolean requiresTransform(Expression exp) {
            return exp instanceof IndexExpression;
        }

        @Override
        protected Expression newTransform() {
            return new LowerCaseExpression();
        }
    }
}
