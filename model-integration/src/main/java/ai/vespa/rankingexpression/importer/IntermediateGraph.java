// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.rankingexpression.importer;

import ai.vespa.rankingexpression.importer.operations.IntermediateOperation;
import ai.vespa.rankingexpression.importer.operations.MatMul;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Holds an intermediate representation of an imported model graph.
 * After this intermediate representation is constructed, it is used to
 * simplify and optimize the computational graph and then converted into the
 * final ImportedModel that holds the Vespa ranking expressions for the model.
 *
 * @author lesters
 */
public class IntermediateGraph {

    private final String modelName;
    private final Map<String, IntermediateOperation> operations = new HashMap<>();
    private final Map<String, GraphSignature> signatures = new HashMap<>();

    private static class GraphSignature {
        final Map<String, String> inputs = new HashMap<>();
        final Map<String, String> outputs = new HashMap<>();
    }

    public IntermediateGraph(String modelName) {
        this.modelName = modelName;
    }

    public String name() {
        return modelName;
    }

    public IntermediateOperation put(String key, IntermediateOperation operation) {
        return operations.put(key, operation);
    }

    public IntermediateOperation get(String key) {
        return operations.get(key);
    }

    public Set<String> signatures() {
        return signatures.keySet();
    }

    public Map<String, String> inputs(String signature) {
        return signatures.computeIfAbsent(signature, (k) -> new GraphSignature()).inputs;
    }

    public Map<String, String> outputs(String signature) {
        return signatures.computeIfAbsent(signature, (k) -> new GraphSignature()).outputs;
    }

    public String defaultSignature() {
        return "default";
    }

    public boolean alreadyImported(String key) {
        return operations.containsKey(key);
    }

    public Map<String, IntermediateOperation> operations() {
        return operations;
    }

    public void optimize() {
        renameDimensions();
    }

    /**
     * Find dimension names to avoid excessive renaming while evaluating the model.
     */
    private void renameDimensions() {
        DimensionRenamer renamer = new DimensionRenamer(this);
        for (String signature : signatures()) {
            for (String output : outputs(signature).values()) {
                addDimensionNameConstraints(operations.get(output), renamer, new HashSet<>());
            }
        }
        renamer.solve();
        for (String signature : signatures()) {
            for (String output : outputs(signature).values()) {
                renameDimensions(operations.get(output), renamer, new HashSet<>());
            }
        }
    }

    private static void addDimensionNameConstraints(IntermediateOperation operation, DimensionRenamer renamer, Set<String> processed) {
        if (processed.contains(operation.name())) {
            return;
        }
        if (operation.type().isPresent()) {
            operation.inputs().forEach(input -> addDimensionNameConstraints(input, renamer, processed));
            operation.addDimensionNameConstraints(renamer);
            processed.add(operation.name());
        }
    }

    private static void renameDimensions(IntermediateOperation operation, DimensionRenamer renamer, Set<String> processed) {
        if (processed.contains(operation.name())) {
            return;
        }
        if (operation.type().isPresent()) {
            operation.inputs().forEach(input -> renameDimensions(input, renamer, processed));
            operation.renameDimensions(renamer);
            processed.add(operation.name());
        }
    }

    @Override
    public String toString() {
        return "intermediate graph for '" + modelName + "'";
    }

    public String toFullString() {
        StringBuilder b = new StringBuilder();
        for (var input : operations.entrySet())
            b.append(input.getKey()).append(": ").append(input.getValue().toFullString()).append("\n");
        return b.toString();
    }

}
