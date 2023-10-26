// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.graph;

import com.yahoo.component.ComponentId;

import java.util.*;

/**
 * A model graph contains the dependency graph of config models.
 *
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class ModelGraph {

    private final List<ModelNode> modelNodes;
    private final List<ModelNode> roots;
    public ModelGraph(List<ModelNode> modelNodes, List<ModelNode> roots) {
        this.modelNodes = modelNodes;
        this.roots = roots;
    }

    /**
     * Performs a topological sort ot the models stored in this graph. The algorithm is based on Kahn topological sort.
     *
     * @return a sorted list of {@link com.yahoo.config.model.graph.ModelNode} in dependency order.
     */
    public List<ModelNode> topologicalSort() {
        DependencyMap dependencyMap = new DependencyMap(modelNodes);
        Queue<ModelNode> unprocessed = new LinkedList<>();
        unprocessed.addAll(roots);
        List<ModelNode> sortedList = new ArrayList<>();
        while (!unprocessed.isEmpty()) {
            ModelNode sortedNode = unprocessed.remove();
            sortedList.add(sortedNode);
            for (ModelNode node : modelNodes) {
                if (dependencyMap.dependsOn(node, sortedNode)) {
                    dependencyMap.removeDependency(node, sortedNode);
                    if (!dependencyMap.hasDependencies(node)) {
                        unprocessed.add(node);
                    }
                }
            }
        }
        for (ModelNode node : modelNodes) {
            if (dependencyMap.hasDependencies(node)) {
                throw new IllegalArgumentException("Unable to sort graph because it contains cycles");
            }
        }
        return sortedList;
    }

    List<ModelNode> getNodes() {
        return modelNodes;
    }

    private static class DependencyMap {
        private final Map<ComponentId, Set<ComponentId>> map = new LinkedHashMap<>();
        DependencyMap(List<ModelNode> modelNodes) {
            for (ModelNode node : modelNodes) {
                Set<ComponentId> ids = new LinkedHashSet<>();
                ids.addAll(node.listDependencyIds());
                map.put(node.id, ids);
            }
        }

        public boolean dependsOn(ModelNode node, ModelNode sortedNode) {
            return map.get(node.id).contains(sortedNode.id);
        }

        public void removeDependency(ModelNode node, ModelNode sortedNode) {
            map.get(node.id).remove(sortedNode.id);
        }

        public boolean hasDependencies(ModelNode node) {
            return !map.get(node.id).isEmpty();
        }
    }
}

