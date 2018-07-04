// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.chain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 */
public final class ChainBuilder<T> {

    private final String chainId;
    private final List<T> components = new ArrayList<>();
    private final IdentityHashMap<T, Dependencies<T>> dependencies = new IdentityHashMap<>();

    public ChainBuilder(String chainId) {
        this.chainId = chainId;
    }

    @SafeVarargs
    public final ChainBuilder<T> add(T component, Dependencies<? extends T>... dependenciesList) {
        if (dependencies.containsKey(component)) {
            throw new IllegalArgumentException("The same component cannot be added twice: " + component);
        }
        components.add(component);

        List<Dependencies<? extends T>> allDependencies =
                Dependencies.allOf(dependenciesList, Dependencies.getAnnotatedDependencies(component));
        dependencies.put(component, Dependencies.union(allDependencies));

        return this;
    }

    public Chain<T> build() {
        DirectedGraph graph = buildGraph();
        List<Vertex> sortedVertices = graph.topologicalSort();
        return new Chain<>(chainId, components(sortedVertices));
    }

    private DirectedGraph buildGraph() {
        DirectedGraph graph = new DirectedGraph();
        List<ComponentVertex<T>> vertices = createVertices();

        addVertices(graph, vertices);
        addEdges(graph, vertices, dependencies);

        return graph;
    }

    private List<ComponentVertex<T>> createVertices() {
        List<ComponentVertex<T>> vertices = new ArrayList<>();
        for (T component : components) {
            vertices.add(new ComponentVertex<>(component));
        }
        return vertices;
    }

    @SuppressWarnings("unchecked")
    private List<T> components(List<Vertex> sortedVertices) {
        List<T> result = new ArrayList<>();
        for (Vertex vertex : sortedVertices) {
            if (vertex instanceof ComponentVertex) {
                result.add((T)((ComponentVertex)vertex).component);
            }
        }
        return result;
    }

    // TODO: create subclasses Beginning/EdingVertex instead? We could then create the correct class in createVertices,
    // TODO: and call the same method in DirGraph for all types.
    private void addVertices(DirectedGraph graph, List<ComponentVertex<T>> vertices) {
        for (ComponentVertex<T> v : vertices) {
            if (isBeginningVertex(v)) {
                graph.addBeginningVertex(v);
            } else if (isEndingVertex(v)) {
                graph.addEndingVertex(v);
            } else {
                graph.addVertex(v);
            }
        }
    }

    private boolean isBeginningVertex(ComponentVertex<T> v) {
        return dependencies.get(v.component).before.providedNames.contains("*");
    }

    private boolean isEndingVertex(ComponentVertex<T> v) {
        return dependencies.get(v.component).after.providedNames.contains("*");
    }

    private static <T> void addEdges(DirectedGraph graph,
                                     List<ComponentVertex<T>> vertices,
                                     IdentityHashMap<T, Dependencies<T>> dependencies) {
        addBeforeInstanceEdges(graph, vertices, dependencies);
        addAfterInstanceEdges(graph, vertices, dependencies);
        addBeforeClassEdges(graph, vertices, dependencies);
        addAfterClassEdges(graph, vertices, dependencies);
        addBeforeProvidedEdges(graph, vertices, dependencies);
        addAfterProvidedEdges(graph, vertices, dependencies);
    }

    // NOTE: When reading 'beforeVertex' below, think that 'vertex' should be _before_ beforeVertex.

    private static <T> void addBeforeClassEdges(DirectedGraph graph,
                                                List<ComponentVertex<T>> vertices,
                                                IdentityHashMap<T, Dependencies<T>> dependencies) {
        for (ComponentVertex<T> vertex : vertices) {
            for (Class<? extends T> beforeClass : dependencies.get(vertex.component).before.classes) {
                for (Vertex beforeVertex : componentsWithClass(vertices, beforeClass)) {
                    graph.addEdge(vertex, beforeVertex);
                }
            }
        }
    }

    private static <T> void addAfterClassEdges(DirectedGraph graph,
                                               List<ComponentVertex<T>> vertices,
                                               IdentityHashMap<T, Dependencies<T>> dependencies) {
        for (ComponentVertex<T> vertex : vertices) {
            for (Class<? extends T> afterClass : dependencies.get(vertex.component).after.classes) {
                for (Vertex afterVertex : componentsWithClass(vertices, afterClass)) {
                    graph.addEdge(afterVertex, vertex);
                }
            }
        }
    }

    private static <T> List<Vertex> componentsWithClass(List<ComponentVertex<T>> vertices,
                                                        Class<? extends T> beforeClass) {
        List<Vertex> result = new ArrayList<>();
        for (ComponentVertex<T> vertex : vertices) {
            if (beforeClass.isInstance(vertex.component)) {
                result.add(vertex);
            }
        }
        return result;
    }

    private static <T> void addBeforeInstanceEdges(DirectedGraph graph,
                                                   List<ComponentVertex<T>> vertices,
                                                   IdentityHashMap<T, Dependencies<T>> dependencies) {
        IdentityHashMap<T, Vertex> componentToVertex = getComponentToVertex(vertices);
        for (ComponentVertex<T> vertex : vertices) {
            for (T before : dependencies.get(vertex.component).before.instances) {
                Vertex beforeVertex = componentToVertex.get(before);
                if (beforeVertex != null) {
                    graph.addEdge(vertex, beforeVertex);
                }
            }
        }
    }

    private static <T> void addAfterInstanceEdges(DirectedGraph graph,
                                                  List<ComponentVertex<T>> vertices,
                                                  IdentityHashMap<T, Dependencies<T>> dependencies) {
        IdentityHashMap<T, Vertex> componentToVertex = getComponentToVertex(vertices);
        for (ComponentVertex<T> vertex : vertices) {
            for (T after : dependencies.get(vertex.component).after.instances) {
                Vertex afterVertex = componentToVertex.get(after);
                if (afterVertex != null) {
                    graph.addEdge(afterVertex, vertex);
                }
            }
        }
    }

    private static <T> IdentityHashMap<T, Vertex> getComponentToVertex(List<ComponentVertex<T>> vertices) {
        IdentityHashMap<T, Vertex> result = new IdentityHashMap<>();
        for (ComponentVertex<T> vertex : vertices) {
            result.put(vertex.component, vertex);
        }
        return result;
    }

    private static <T> void addBeforeProvidedEdges(DirectedGraph graph,
                                                   List<ComponentVertex<T>> vertices,
                                                   IdentityHashMap<T, Dependencies<T>> dependencies) {
        Map<String, Set<Vertex>> providedNames = getProvidedNames(vertices, dependencies);
        for (ComponentVertex<T> vertex : vertices) {
            for (String name : dependencies.get(vertex.component).before.providedNames) {
                for (Vertex beforeVertex : emptyIfNull(providedNames.get(name))) {
                    graph.addEdge(vertex, beforeVertex);
                }
            }
        }
    }

    private static <T> void addAfterProvidedEdges(DirectedGraph graph,
                                                  List<ComponentVertex<T>> vertices,
                                                  IdentityHashMap<T, Dependencies<T>> dependencies) {
        Map<String, Set<Vertex>> providedNames = getProvidedNames(vertices, dependencies);
        for (ComponentVertex<T> vertex : vertices) {
            for (String name : dependencies.get(vertex.component).after.providedNames) {
                for (Vertex afterVertex : emptyIfNull(providedNames.get(name))) {
                    graph.addEdge(afterVertex, vertex);
                }
            }
        }
    }

    private static <T> Map<String, Set<Vertex>> getProvidedNames(List<ComponentVertex<T>> vertices,
                                                                 IdentityHashMap<T, Dependencies<T>> dependencies) {
        Map<String, Set<Vertex>> result = new HashMap<>();
        for (ComponentVertex<T> vertex : vertices) {
            for (String providedName : dependencies.get(vertex.component).provided) {
                getIdentitySet(result, providedName).add(vertex);
            }
            addClassName(result, vertex);
        }
        return result;
    }

    private static void addClassName(Map<String, Set<Vertex>> providedNamesToVertex, ComponentVertex<?> vertex) {
        String className = vertex.component.getClass().getName();
        getIdentitySet(providedNamesToVertex, className).add(vertex);
    }

    private static <T> Set<T> getIdentitySet(Map<String, Set<T>> map, String key) {
        Set<T> result = map.get(key);
        if (result == null) {
            result = Collections.newSetFromMap(new IdentityHashMap<T, Boolean>());
            map.put(key, result);
        }
        return result;
    }

    private static <T> Set<T> emptyIfNull(Set<T> set) {
        return set != null ?
               set :
               Collections.<T>emptySet();
    }

    private static class ComponentVertex<T> implements Vertex {

        final T component;

        private ComponentVertex(T component) {
            this.component = component;
        }
    }
}
