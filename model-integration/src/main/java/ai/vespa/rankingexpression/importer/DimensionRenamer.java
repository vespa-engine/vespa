// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer;

import ai.vespa.rankingexpression.importer.operations.IntermediateOperation;
import ai.vespa.rankingexpression.importer.operations.Rename;
import com.yahoo.collections.ListMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A constraint solver which finds suitable dimension names to reduce the
 * amount of necessary renaming during evaluation of an imported model.
 *
 * @author lesters
 * @author bratseth
 */
public class DimensionRenamer {

    private static final Logger log = Logger.getLogger(DimensionRenamer.class.getName());

    private final String dimensionPrefix;

    /** The graph we are renaming the dimensions of */
    private final IntermediateGraph graph;

    /** The set of dimensions to find a solution for */
    private final Set<String> dimensions = new HashSet<>();

    /** The constraints on the dimension name assignment */
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

    /** Add a dimension to the set of dimensions to be renamed */
    public void addDimension(String name) { dimensions.add(name); }

    /** Add a constraint between dimension names */
    public void addConstraint(String from, String to, Constraint constraint, IntermediateOperation operation) {
        if (constraint instanceof EqualConstraint && from.equals(to)) return;

        Arc arc = new Arc(from, to, operation);
        constraints.put(arc, constraint);
        constraints.put(arc.opposite(), constraint.opposite());  // make constraint graph symmetric
    }

    void solve() {
        log.log(Level.FINE, () -> "Rename problem:\n" + constraintsToString(constraints));
        renames = solve(100000);
        log.log(Level.FINE, () -> "Rename solution:\n" + renamesToString(renames));
    }

    private Map<String, Integer> solve(int maxIterations) {
        Map<String, Integer> solution = solveWithOrWithoutSoftConstraints(maxIterations);
        if (solution != null) return solution;

        for (RenameTarget target : prioritizedRenameTargets()) {
            target.insertRename(this);
            solution = solveWithOrWithoutSoftConstraints(maxIterations);
            if (solution != null) return solution;
            target.uninsertRename(this);
        }
        throw new IllegalArgumentException("Could not find a dimension naming solution " +
                                           "given constraints\n" + constraintsToString(constraints));
    }

    private Map<String, Integer> solveWithOrWithoutSoftConstraints(int maxIterations) {
        Map<String, Integer> solution = NamingConstraintSolver.solve(dimensions, constraints, maxIterations);
        if ( solution == null) {
            ListMap<Arc, Constraint> hardConstraints = new ListMap<>();
            boolean anyRemoved = copyHard(constraints, hardConstraints);
            if (anyRemoved) {
                solution = NamingConstraintSolver.solve(dimensions, hardConstraints, maxIterations);
            }
        }
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

    private List<RenameTarget> prioritizedRenameTargets() {
        Map<IntermediateOperation, Integer> constraintsPerOperation = new HashMap<>();

        for (var constraint : constraints.entrySet()) {
            constraintsPerOperation.compute(constraint.getKey().operation,
                                            (operation, count) -> count == null ? 1 : ++count);
        }
        List<IntermediateOperation> prioritizedOperations =
                constraintsPerOperation.entrySet().stream()
                                                  .sorted(Comparator.comparingInt(entry -> - entry.getValue()))
                                                  .map(Map.Entry::getKey)
                                                  .collect(Collectors.toList());

        List<RenameTarget> targets = new ArrayList<>();
        for (IntermediateOperation operation : prioritizedOperations) {
            for (int i = 0; i < operation.inputs().size(); i++) {
                Optional<OrderedTensorType> inputType = operation.inputs().get(i).type();
                if (inputType.isEmpty()) continue;
                for (String dimensionName : inputType.get().dimensionNames()) {
                    RenameTarget target = new RenameTarget(operation, i, dimensionName, graph);
                    targets.add(target);
                }
            }
        }
        return targets;
    }

    /**
     * Retrieve resulting name of a dimension after solving for constraints, or empty if no
     * solution is found yet, or this dimension was not added before finding a solution.
     */
    public Optional<String> dimensionNameOf(String name) {
        if ( renames == null || ! renames.containsKey(name))
            return Optional.empty();
        return Optional.of(String.format("%s%d", dimensionPrefix, renames.get(name)));
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

    /**
     * An operation and an input number which we may want to insert a rename operation at.
     * That is, we may want to change op(..., input, ...) to op(..., rename(input), ...).
     *
     * This class is (and must remain) immutable.
     */
    private static class RenameTarget {

        final IntermediateOperation operation;
        final int inputNumber;
        final String dimensionName;
        final IntermediateGraph graph;

        public RenameTarget(IntermediateOperation operation, int inputNumber, String dimensionName, IntermediateGraph graph) {
            this.operation = operation;
            this.inputNumber = inputNumber;
            this.dimensionName = dimensionName;
            this.graph = graph;
        }

        public IntermediateOperation input() {
            return operation.inputs().get(inputNumber);
        }

        /** Inserts a rename operation if possible. Returns whether an operation was inserted. */
        private boolean insertRename(DimensionRenamer renamer) {
            Rename rename = new Rename(operation.modelName(),
                                       dimensionName,
                                       renamer.dimensionPrefix + renamer.dimensions.size(),
                                       input());
            operation.insert(rename, inputNumber);
            removeConstraintsOf(operation, renamer);
            rename.addDimensionNameConstraints(renamer);
            operation.addDimensionNameConstraints(renamer);
            return true;
        }

        /** Undo what insertRenameOperation has done: Set back the original operation and remove+add constraints */
        private void uninsertRename(DimensionRenamer renamer) {
            Rename rename = (Rename)operation.inputs().get(inputNumber);
            operation.uninsert(inputNumber);
            removeConstraintsOf(rename, renamer);
            removeConstraintsOf(operation, renamer);
            operation.addDimensionNameConstraints(renamer);
        }

        private void removeConstraintsOf(IntermediateOperation operation, DimensionRenamer renamer) {
            for (Arc key : new HashSet<>(renamer.constraints.keySet())) {
                if (key.operation == operation)
                    renamer.constraints.removeAll(key);
            }
        }

        @Override
        public String toString() {
            return operation + ", input " + inputNumber;
        }

    }

}
