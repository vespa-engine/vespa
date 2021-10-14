// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.trace;

import org.junit.Test;

/**
 * @author Simon Thoresen Hult
 */
public class TraceVisitorTestCase {

    @Test
    public void requireThatTraceVisitorCompilesWithOnlyVisitImplemented() {
        new TraceNode(null, 0).accept(new TraceVisitor() {

            @Override
            public void visit(TraceNode node) {

            }
        });
    }
}
