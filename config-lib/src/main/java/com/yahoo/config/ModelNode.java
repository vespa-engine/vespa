// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a 'model' in a {@link ConfigInstance}.
 *
 * @author bratseth
 */
public class ModelNode extends LeafNode<Path> {

    private final ModelReference reference;

    public ModelNode() {
        this.value = null;
        this.reference = null;
    }

    public ModelNode(ModelReference modelReference) {
        super(true);
        this.value = modelReference.value(); // The resolved value, or null if not resolved
        this.reference = modelReference;
    }

    @Override
    public String getValue() {
        return reference.toString();
    }

    @Override
    public String toString() {
        return (value == null) ? "(null)" : '"' + getValue() + '"';
    }

    @Override
    protected boolean doSetValue(String stringVal) {
        throw new UnsupportedOperationException();
    }

    public ModelReference getModelReference() {
        return reference;
    }

    public static List<ModelReference> toModelReferences(List<ModelNode> modelNodes) {
        List<ModelReference> modelReferences = new ArrayList<>();
        for (ModelNode modelNode : modelNodes)
            modelReferences.add(modelNode.getModelReference());
        return modelReferences;
    }

    public static Map<String, ModelReference> toModelReferenceMap(Map<String, ModelNode> nodeMap) {
        Map<String, ModelReference> referenceMap = new LinkedHashMap<>();
        for (var entry : nodeMap.entrySet()) {
            referenceMap.put(entry.getKey(), entry.getValue().getModelReference());
        }
        return referenceMap;
    }

}
