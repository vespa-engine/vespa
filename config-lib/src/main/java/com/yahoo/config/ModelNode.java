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
public class ModelNode extends LeafNode<ModelReference> {

    public ModelNode() {
        this.value = null;
    }

    public ModelNode(ModelReference modelReference) {
        super(true);
        this.value = modelReference;
    }

    public ModelReference value() {
        return value;
    }

    @Override
    public String getValue() {
        return value.toString();
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
        return value;
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
