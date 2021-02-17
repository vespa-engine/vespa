// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.chain.dependencies.ordering;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Signals that the dependency graph contains cycles. A DOT language
 * representation of the cycle is available to help solve the problem (<a
 * href="http://graphviz.org/">GraphViz</a>).
 *
 * @author Tony Vaagenes
 */
@SuppressWarnings("serial")
public class CycleDependenciesException extends RuntimeException {

    public Map<String, NameProvider> cycleNodes;

    CycleDependenciesException(Map<String, NameProvider> cycleNodes) {
        super("The following set of dependencies lead to a cycle:\n"
                + createDotString(cycleNodes));
        this.cycleNodes = cycleNodes;
    }

    private static String createDotString(Map<String, NameProvider> cycleNodes) {
        StringBuilder res = new StringBuilder();
        res.append("digraph dependencyGraph {\n");

        Set<Node> used = new HashSet<>();
        for (Node node: cycleNodes.values()) {
            if (!node.ready())
                node.dotDependenciesString(res, used);

        }
        res.append("}");
        return res.toString();
    }


    public String dotString() {
        return createDotString(cycleNodes);
    }

}
