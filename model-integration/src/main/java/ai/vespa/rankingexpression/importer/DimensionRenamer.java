// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer;

import ai.vespa.rankingexpression.importer.operations.IntermediateOperation;
import com.yahoo.collections.ListMap;
import com.yahoo.lang.MutableInteger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A constraint satisfier to find suitable dimension names to reduce the
 * amount of necessary renaming during evaluation of an imported model.
 *
 * @author lesters
 */
public class DimensionRenamer {

    private final String dimensionPrefix;
    private final ListMap<String, Integer> variables = new ListMap<>();
    private final Map<Arc, Constraint> constraints = new HashMap<>();

    /** The solution to this, or null if no solution is found (yet) */
    private Map<String, Integer> renames = null;

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
        variables.put(name);
    }

    /**
     * Add a constraint between dimension names.
     */
    public void addConstraint(String from, String to, Constraint constraint, IntermediateOperation operation) {
        Arc arc = new Arc(from, to, operation);
        constraints.put(arc, constraint);
        constraints.put(arc.opposite(), constraint.opposite());  // make constraint graph symmetric
    }

    /**
     * Retrieve resulting name of dimension after solving for constraints.
     */
    public Optional<String> dimensionNameOf(String name) {
        if ( ! renames.containsKey(name)) {
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
     *
     * @return the solution in the form of the renames to perform
     */
    private Map<String, Integer> solve(int maxIterations) {
        variables.freeze();
        Map<String, Integer> renames = new HashMap<>();

        // Todo: evaluate possible improved efficiency by using a heuristic such as min-conflicts
        boolean solved = trySolve(variables, constraints, maxIterations, renames);
        if ( ! solved) {
            renames.clear();
            Map<Arc, Constraint> hardConstraints = new HashMap<>();
            constraints.entrySet().stream().filter(e ->  ! e.getValue().isSoft())
                                           .forEach(e -> hardConstraints.put(e.getKey(), e.getValue()));
            if (hardConstraints.size() < constraints.size())
                solved = trySolve(variables, hardConstraints, maxIterations, renames);
        }
        if ( ! solved) {
            throw new IllegalArgumentException("Could not find a dimension naming solution" +
                                               " given constraints\n" + constraintsToString());
        }

        // Todo: handle failure more gracefully:
        // If a solution can't be found, look at the operation node in the arc
        // with the most remaining constraints, and inject a rename operation.
        // Then run this algorithm again.

        return renames;
    }

    /** Try the solve the constraint problem given in the arguments, and put the result in renames */
    private static boolean trySolve(ListMap<String, Integer> inputVariables,
                                    Map<Arc, Constraint> constraints,
                                    int maxIterations,
                                    Map<String, Integer> renames) {
        var variables = new ListMap<>(inputVariables);
        initialize(variables);
        MutableInteger iterations = new MutableInteger(0);
        for (String dimension : variables.keySet()) {
            List<Integer> values = variables.get(dimension);
            if (values.size() > 1) {
                if ( ! ac3(iterations, variables, constraints)) return false;
                values.sort(Integer::compare);
                variables.put(dimension, values.get(0));
            }
            renames.put(dimension, variables.get(dimension).get(0));
            if (iterations.get() > maxIterations) return false;
        }
        return true;
    }

    void solve() {
        renames = solve(100000);
    }

    private static void initialize(ListMap<String, Integer> variables) {
        for (Map.Entry<String, List<Integer>> variable : variables.entrySet()) {
            List<Integer> values = variable.getValue();
            for (int i = 0; i < variables.size(); ++i) {
                values.add(i);  // invariant: values are in increasing order
            }
        }
    }

    private static boolean ac3(MutableInteger iterations,
                               ListMap<String, Integer> variables,
                               Map<Arc, Constraint> constraints) {
        Deque<Arc> workList = new ArrayDeque<>(constraints.keySet());
        while ( ! workList.isEmpty()) {
            Arc arc = workList.pop();
            iterations.add(1);
            if (revise(arc, variables, constraints)) {
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

    private static boolean revise(Arc arc,
                                  ListMap<String, Integer> variables,
                                  Map<Arc, Constraint> constraints) {
        boolean revised = false;
        for (Iterator<Integer> fromIterator = variables.get(arc.from).iterator(); fromIterator.hasNext(); ) {
            Integer from = fromIterator.next();
            boolean satisfied = false;
            for (Iterator<Integer> toIterator = variables.get(arc.to).iterator(); toIterator.hasNext(); ) {
                Integer to = toIterator.next();
                if (constraints.get(arc).test(from, to)) {
                    satisfied = true;
                }
            }
            if ( ! satisfied) {
                fromIterator.remove();
                revised = true;
            }
        }
        return revised;
    }

    private String constraintsToString() {
        return constraints.entrySet().stream()
                                     .filter(e -> ! e.getValue().isOpposite())
                                     .map(e -> e.getKey().from + " " + e.getValue() + " " + e.getKey().to + " (origin: " + e.getKey().operation + ")")
                                     .collect(Collectors.joining("\n"));
    }

    private static class Arc {

        private final String from;
        private final String to;
        private final IntermediateOperation operation;

        Arc(String from, String to, IntermediateOperation operation) {
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
            if (!(obj instanceof Arc)) {
                return false;
            }
            Arc other = (Arc) obj;
            return Objects.equals(from, other.from) && Objects.equals(to, other.to);
        }

        @Override
        public String toString() {
            return from + " -> " + to;
        }
    }

    public static abstract class Constraint {

        private final boolean soft, opposite;

        protected Constraint(boolean soft, boolean opposite) {
            this.soft = soft;
            this.opposite = opposite;
        }

        abstract boolean test(Integer x, Integer y);
        abstract Constraint opposite();

        /** Returns whether this constraint can be violated if that is necessary to achieve a solution */
        boolean isSoft() { return soft; }

        /** Returns whether this is an opposite of another constraint */
        boolean isOpposite() { return opposite; }

        public static Constraint equal(boolean soft) { return new EqualConstraint(soft, false); }
        public static Constraint lessThan(boolean soft) { return new LessThanConstraint(soft, false); }
        public static Constraint greaterThan(boolean soft) { return new GreaterThanConstraint(soft, false); }

    }

    private static class EqualConstraint extends Constraint {

        private EqualConstraint(boolean soft, boolean opposite) {
            super(soft, opposite);
        }

        @Override
        public boolean test(Integer x, Integer y) { return Objects.equals(x, y); }

        @Override
        public Constraint opposite() { return new EqualConstraint(isSoft(), true); }

        @Override
        public String toString() { return "=="; }

    }

    private static class LessThanConstraint extends Constraint {

        private LessThanConstraint(boolean soft, boolean opposite) {
            super(soft, opposite);
        }

        @Override
        public boolean test(Integer x, Integer y) { return x < y; }

        @Override
        public Constraint opposite() { return new GreaterThanConstraint(isSoft(), true); }

        @Override
        public String toString() { return "<"; }

    }

    private static class GreaterThanConstraint extends Constraint {

        private GreaterThanConstraint(boolean soft, boolean opposite) {
            super(soft, opposite);
        }

        @Override
        public boolean test(Integer x, Integer y) { return x > y; }

        @Override
        public Constraint opposite() { return new LessThanConstraint(isSoft(), true); }

        @Override
        public String toString() { return ">"; }

    }

}
