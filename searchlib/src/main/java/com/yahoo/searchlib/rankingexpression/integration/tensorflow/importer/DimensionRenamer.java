// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer;

import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations.TensorFlowOperation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A constraint satisfier to find suitable dimension names to reduce the
 * amount of necessary renaming during evaluation of an imported model.
 *
 * @author lesters
 */
public class DimensionRenamer {

    private final String dimensionPrefix;
    private final Map<String, List<Integer>> variables = new HashMap<>();
    private final Map<Arc, Constraint> constraints = new HashMap<>();
    private final Map<String, Integer> renames = new HashMap<>();

    private int iterations = 0;

    public DimensionRenamer() {
        this("d");
    }

    public DimensionRenamer(String dimensionPrefix) {
        this.dimensionPrefix = dimensionPrefix;
    }

    /**
     * Add a dimension name variable.
     */
    public void addDimension(String name) {
        variables.computeIfAbsent(name, d -> new ArrayList<>());
    }

    /**
     * Add a constraint between dimension names.
     */
    public void addConstraint(String from, String to, Constraint pred, TensorFlowOperation operation) {
        Arc arc = new Arc(from, to, operation);
        Arc opposite = arc.opposite();
        constraints.put(arc, pred);
        constraints.put(opposite, (x,y) -> pred.test(y, x));  // make constraint graph symmetric
    }

    /**
     * Retrieve resulting name of dimension after solving for constraints.
     */
    public Optional<String> dimensionNameOf(String name) {
        if (!renames.containsKey(name)) {
            return Optional.empty();
        }
        return Optional.of(String.format("%s%d", dimensionPrefix, renames.get(name)));
    }

    /**
     * Perform iterative arc consistency until we have found a solution. After
     * an initial iteration, the variables (dimensions) will have multiple
     * valid values. Find a single valid assignment by iteratively locking one
     * dimension after another, and running the arc consistency algorithm
     * multiple times.
     *
     * This requires having constraints that result in an absolute ordering:
     * equals, lesserThan and greaterThan do that, but adding notEquals does
     * not typically result in a guaranteed ordering. If that is needed, the
     * algorithm below needs to be adapted with a backtracking (tree) search
     * to find solutions.
     */
    public void solve(int maxIterations) {
        initialize();

        // Todo: evaluate possible improved efficiency by using a heuristic such as min-conflicts

        for (String dimension : variables.keySet()) {
            List<Integer> values = variables.get(dimension);
            if (values.size() > 1) {
                if (!ac3()) {
                    throw new IllegalArgumentException("Dimension renamer unable to find a solution.");
                }
                values.sort(Integer::compare);
                variables.put(dimension, Collections.singletonList(values.get(0)));
            }
            renames.put(dimension, variables.get(dimension).get(0));
            if (iterations > maxIterations) {
                throw new IllegalArgumentException("Dimension renamer unable to find a solution within " +
                        maxIterations + " iterations");
            }
        }

        // Todo: handle failure more gracefully:
        // If a solution can't be found, look at the operation node in the arc
        // with the most remaining constraints, and inject a rename operation.
        // Then run this algorithm again.
    }

    public void solve() {
        solve(100000);
    }

    private void initialize() {
        for (Map.Entry<String, List<Integer>> variable : variables.entrySet()) {
            List<Integer> values = variable.getValue();
            for (int i = 0; i < variables.size(); ++i) {
                values.add(i);  // invariant: values are in increasing order
            }
        }
    }

    private boolean ac3() {
        Deque<Arc> workList = new ArrayDeque<>(constraints.keySet());
        while (!workList.isEmpty()) {
            Arc arc = workList.pop();
            iterations += 1;
            if (revise(arc)) {
                if (variables.get(arc.from).size() == 0) {
                    return false;  // no solution found
                }
                for (Arc constraint : constraints.keySet()) {
                    if (arc.from.equals(constraint.to) && !arc.to.equals(constraint.from)) {
                        workList.add(constraint);
                    }
                }
            }
        }
        return true;
    }

    private boolean revise(Arc arc) {
        boolean revised = false;
        for(Iterator<Integer> fromIterator = variables.get(arc.from).iterator(); fromIterator.hasNext(); ) {
            Integer from = fromIterator.next();
            boolean satisfied = false;
            for (Iterator<Integer> toIterator = variables.get(arc.to).iterator(); toIterator.hasNext(); ) {
                Integer to = toIterator.next();
                if (constraints.get(arc).test(from, to)) {
                    satisfied = true;
                }
            }
            if (!satisfied) {
                fromIterator.remove();
                revised = true;
            }
        }
        return revised;
    }

    public interface Constraint {
        boolean test(Integer x, Integer y);
    }

    public static boolean equals(Integer x, Integer y) {
        return Objects.equals(x, y);
    }

    public static boolean lesserThan(Integer x, Integer y) {
        return x < y;
    }

    public static boolean greaterThan(Integer x, Integer y) {
        return x > y;
    }

    private static class Arc {

        private final String from;
        private final String to;
        private final TensorFlowOperation operation;

        Arc(String from, String to, TensorFlowOperation operation) {
            this.from = from;
            this.to = to;
            this.operation = operation;
        }

        Arc opposite() {
            return new Arc(to, from, operation);
        }

        @Override
        public int hashCode() {
            return Objects.hash(from, to);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || !(obj instanceof Arc)) {
                return false;
            }
            Arc other = (Arc) obj;
            return Objects.equals(from, other.from) && Objects.equals(to, other.to);
        }

        @Override
        public String toString() {
            return String.format("%s -> %s", from, to);
        }
    }

}
