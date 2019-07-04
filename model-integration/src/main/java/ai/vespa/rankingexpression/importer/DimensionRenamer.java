// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer;

import ai.vespa.rankingexpression.importer.operations.IntermediateOperation;
import ai.vespa.rankingexpression.importer.operations.MatMul;
import ai.vespa.rankingexpression.importer.operations.Rename;
import com.yahoo.collections.ListMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A constraint solver which finds suitable dimension names to reduce the
 * amount of necessary renaming during evaluation of an imported model.
 *
 * @author lesters
 */
public class DimensionRenamer {

    private static final Logger log = Logger.getLogger(DimensionRenamer.class.getName());

    private final String dimensionPrefix;

    /** The graph we are renaming the dimensions of */
    private final IntermediateGraph graph;

    private final ListMap<String, Integer> variables = new ListMap<>();
    private final ListMap<Arc, Constraint> constraints = new ListMap<>();

    /** The solution to this, or null if no solution is found yet */
    private Map<String, Integer> renames = null;

    public DimensionRenamer(IntermediateGraph graph) {
        this(graph, "d");
    }

    public DimensionRenamer(IntermediateGraph graph, String dimensionPrefix) {
        this.graph = graph;
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
     * equal, lessThan and greaterThan do that, but adding notEqual does
     * not typically result in a guaranteed ordering. If that is needed, the
     * algorithm below needs to be adapted with a backtracking (tree) search
     * to find solutions.
     *
     * @return the solution in the form of the renames to perform
     */
    private Map<String, Integer> solve(int maxIterations) {
        Map<String, Integer> solution = NamingConstraintSolver.solve(variables, constraints, maxIterations);
        if ( solution == null) {
            IntermediateOperation operation = graph.operations().get("dense_out/MatMul");
            if (operation != null && operation instanceof MatMul) {
                IntermediateOperation arg0 = operation.inputs().get(0);
                List<IntermediateOperation> inputs = new ArrayList<>(operation.inputs());
                inputs.set(0, new Rename(arg0.modelName(), "Dot_ExpandDims_1", "renamed_0", arg0));
                IntermediateOperation newOperation = operation.withInputs(inputs);
                graph.put("dense_out/MatMul", newOperation);

                for (Arc key : new HashSet<>(constraints.keySet())) {
                    if (key.operation == operation)
                        constraints.removeAll(key);
                }
                addDimension("renamed_0");
                newOperation.addDimensionNameConstraints(this);

                solution = NamingConstraintSolver.solve(variables, constraints, maxIterations);
            }
        }
        if ( solution == null) {
            ListMap<Arc, Constraint> hardConstraints = new ListMap<>();
            boolean anyRemoved = copyHard(constraints, hardConstraints);
            if (anyRemoved)
                solution = NamingConstraintSolver.solve(variables, hardConstraints, maxIterations);
            if ( solution == null) {
                throw new IllegalArgumentException("Could not find a dimension naming solution " +
                                                   "given constraints\n" + constraintsToString(hardConstraints));
            }
        }

        // Todo: handle failure more gracefully:
        // If a solution can't be found, look at the operation node in the arc
        // with the most remaining constraints, and inject a rename operation.
        // Then run this algorithm again.

        return solution;
    }

    /** Removes soft constraints and returns whether something was removed */
    private boolean copyHard(ListMap<Arc, Constraint> source, ListMap<Arc, Constraint> target) {
        boolean removed = false;
        for (var entry : source.entrySet()) {
            Arc arc = entry.getKey();
            for (Constraint constraint : entry.getValue()) {
                if ( ! constraint.isSoft())
                    target.put(arc, constraint);
                else
                    removed = true;
            }
        }
        return removed;
    }

    void solve() {
        log.log(Level.FINE, () -> "Rename problem:\n" + constraintsToString(constraints));
        renames = solve(100000);
        log.log(Level.FINE, () -> "Rename solution:\n" + renamesToString(renames));
    }

    private static String renamesToString(Map<String, Integer> renames) {
        return renames.entrySet().stream()
                                 .map(e -> "  " + e.getKey() + " -> " + e.getValue())
                                 .collect(Collectors.joining("\n"));
    }

    private static String constraintsToString(ListMap<Arc, Constraint> constraints) {
        StringBuilder b = new StringBuilder();
        for (var entry : constraints.entrySet()) {
            Arc arc = entry.getKey();
            for (Constraint constraint : entry.getValue()) {
                if (constraint.isOpposite()) continue; // noise
                b.append("  ");
                if (constraint.isSoft())
                    b.append("(soft) ");
                b.append(arc.from).append(" ").append(constraint).append(" ").append(arc.to);
                b.append("  (origin: ").append(arc.operation).append(")\n");
            }
        }
        return b.toString();
    }

    static class Arc {

        final String from;
        final String to;
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
        public static Constraint notEqual(boolean soft) { return new NotEqualConstraint(soft, false); }
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

    private static class NotEqualConstraint extends Constraint {

        private NotEqualConstraint(boolean soft, boolean opposite) {
            super(soft, opposite);
        }

        @Override
        public boolean test(Integer x, Integer y) { return ! Objects.equals(x, y); }

        @Override
        public Constraint opposite() { return new NotEqualConstraint(isSoft(), true); }

        @Override
        public String toString() { return "!="; }

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
