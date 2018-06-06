// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.searchlib.rankingexpression.integration.ml.importer;

import com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations.IntermediateOperation;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Holds an intermediate representation of an imported ONNX or TensorFlow
 * graph. After this intermediate representation is constructed, it is used to
 * simplify and optimize the computational graph and then converted into the
 * final ImportedModel that holds the Vespa ranking expressions for the model.
 *
 * @author lesters
 */
public class IntermediateGraph {

    private final String modelName;
    private final Map<String, IntermediateOperation> index = new HashMap<>();
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
        return index.put(key, operation);
    }

    public IntermediateOperation get(String key) {
        return index.get(key);
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
        return index.containsKey(key);
    }

    public Collection<IntermediateOperation> operations() {
        return index.values();
    }

    public void optimize() {
        renameDimensions();
    }

    /**
     * Find dimension names to avoid excessive renaming while evaluating the model.
     */
    private void renameDimensions() {
        DimensionRenamer renamer = new DimensionRenamer();
        for (String signature : signatures()) {
            for (String output : outputs(signature).values()) {
                addDimensionNameConstraints(index.get(output), renamer);
            }
        }
        renamer.solve();
        for (String signature : signatures()) {
            for (String output : outputs(signature).values()) {
                renameDimensions(index.get(output), renamer);
            }
        }
    }

    private static void addDimensionNameConstraints(IntermediateOperation operation, DimensionRenamer renamer) {
        if (operation.type().isPresent()) {
            operation.inputs().forEach(input -> addDimensionNameConstraints(input, renamer));
            operation.addDimensionNameConstraints(renamer);
        }
    }

    private static void renameDimensions(IntermediateOperation operation, DimensionRenamer renamer) {
        if (operation.type().isPresent()) {
            operation.inputs().forEach(input -> renameDimensions(input, renamer));
            operation.renameDimensions(renamer);
        }
    }

}
