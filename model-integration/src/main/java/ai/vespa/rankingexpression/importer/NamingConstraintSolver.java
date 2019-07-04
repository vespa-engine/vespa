// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer;

import com.yahoo.collections.ListMap;
import com.yahoo.lang.MutableInteger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Solves a dimension naming constraint problem.
 *
 * @author lesters
 * @author bratseth
 */
class NamingConstraintSolver {

    private final ListMap<String, Integer> variables;
    private final ListMap<DimensionRenamer.Arc, DimensionRenamer.Constraint> constraints;

    private int iterations = 0;
    private final int maxIterations;

    private NamingConstraintSolver(ListMap<String, Integer> inputVariables,
                                   ListMap<DimensionRenamer.Arc, DimensionRenamer.Constraint> constraints,
                                   int maxIterations) {
        this.variables = new ListMap<>(inputVariables);
        initialize(variables);
        this.constraints = constraints;
        this.maxIterations = maxIterations;
    }

    /** Try the solve the constraint problem given in the arguments, and put the result in renames */
    private Map<String, Integer> trySolve() {
        // TODO: Evaluate possible improved efficiency by using a heuristic such as min-conflicts

        Map<String, Integer> solution = new HashMap<>();
        for (String dimension : variables.keySet()) {
            List<Integer> values = variables.get(dimension);
            if (values.size() > 1) {
                if ( ! ac3()) return null;
                values.sort(Integer::compare);
                variables.replace(dimension, values.get(0));
            }
            solution.put(dimension, variables.get(dimension).get(0));
            if (iterations > maxIterations) return null;
        }
        return solution;
    }

    private static void initialize(ListMap<String, Integer> variables) {
        for (Map.Entry<String, List<Integer>> variable : variables.entrySet()) {
            List<Integer> values = variable.getValue();
            for (int i = 0; i < variables.size(); ++i) {
                values.add(i);  // invariant: values are in increasing order
            }
        }
    }

    private boolean ac3() {
        Deque<DimensionRenamer.Arc> workList = new ArrayDeque<>(constraints.keySet());
        while ( ! workList.isEmpty()) {
            DimensionRenamer.Arc arc = workList.pop();
            iterations++;
            if (revise(arc, variables, constraints)) {
                if (variables.get(arc.from).size() == 0) {
                    return false;  // no solution found
                }
                for (DimensionRenamer.Arc constraint : constraints.keySet()) {
                    if (arc.from.equals(constraint.to) && !arc.to.equals(constraint.from)) {
                        workList.add(constraint);
                    }
                }
            }
        }
        return true;
    }

    private static boolean revise(DimensionRenamer.Arc arc,
                                  ListMap<String, Integer> variables,
                                  ListMap<DimensionRenamer.Arc, DimensionRenamer.Constraint> constraints) {
        boolean revised = false;
        for (Iterator<Integer> fromIterator = variables.get(arc.from).iterator(); fromIterator.hasNext(); ) {
            Integer from = fromIterator.next();
            boolean satisfied = false;
            for (Iterator<Integer> toIterator = variables.get(arc.to).iterator(); toIterator.hasNext(); ) {
                Integer to = toIterator.next();
                if (constraints.get(arc).stream().allMatch(constraint -> constraint.test(from, to)))
                    satisfied = true;
            }
            if ( ! satisfied) {
                fromIterator.remove();
                revised = true;
            }
        }
        return revised;
    }

    /**
     * Attempts to solve the given naming problem. The input maps are never modified.
     *
     * @return the solution as a map from existing names to name ids represented as integers, or NULL
     *         if no solution could be found
     */
    public static Map<String, Integer> solve(ListMap<String, Integer> inputVariables,
                                             ListMap<DimensionRenamer.Arc, DimensionRenamer.Constraint> constraints,
                                             int maxIterations) {
        return new NamingConstraintSolver(inputVariables, constraints, maxIterations).trySolve();
    }

}
