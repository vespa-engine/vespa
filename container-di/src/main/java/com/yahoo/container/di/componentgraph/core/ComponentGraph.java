// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.componentgraph.core;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.inject.BindingAnnotation;
import com.google.inject.ConfigurationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.yahoo.collections.Pair;
import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.config.ConfigInstance;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.ConfigKey;
import net.jcip.annotations.NotThreadSafe;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.container.di.componentgraph.core.Exceptions.removeStackTrace;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 * @author ollivir
 */
@NotThreadSafe
public class ComponentGraph {
    private static final Logger log = Logger.getLogger(ComponentGraph.class.getName());

    private long generation;
    private Map<ComponentId, Node> nodesById = new HashMap<>();

    public ComponentGraph(long generation) {
        this.generation = generation;
    }

    public ComponentGraph() {
        this(0L);
    }

    public long generation() {
        return generation;
    }

    public int size() {
        return nodesById.size();
    }

    public Collection<Node> nodes() {
        return nodesById.values();
    }

    public void add(Node component) {
        if (nodesById.containsKey(component.componentId())) {
            throw new IllegalStateException("Multiple components with the same id " + component.componentId());
        }
        nodesById.put(component.componentId(), component);
    }

    private Optional<Node> lookupGlobalComponent(Key<?> key) {
        if (!(key.getTypeLiteral().getType() instanceof Class)) {

            throw new RuntimeException("Type not supported " + key.getTypeLiteral());
        }
        Class<?> clazz = key.getTypeLiteral().getRawType();

        Collection<ComponentNode> components = matchingComponentNodes(nodes(), key);
        if (components.isEmpty()) {
            return Optional.empty();
        } else if (components.size() == 1) {
            return Optional.ofNullable(Iterables.get(components, 0));
        } else {

            List<Node> nonProviderComponents = components.stream().filter(c -> !Provider.class.isAssignableFrom(c.instanceType()))
                    .collect(Collectors.toList());
            if (nonProviderComponents.isEmpty()) {
                throw new IllegalStateException("Multiple global component providers for class '" + clazz.getName() + "' found");
            } else if (nonProviderComponents.size() == 1) {
                return Optional.of(nonProviderComponents.get(0));
            } else {
                throw new IllegalStateException("Multiple global components with class '" + clazz.getName() + "' found");
            }
        }
    }

    public <T> T getInstance(Class<T> clazz) {
        return getInstance(Key.get(clazz));
    }

    @SuppressWarnings("unchecked")
    public <T> T getInstance(Key<T> key) {
        // TODO: Combine exception handling with lookupGlobalComponent.
        Object ob = lookupGlobalComponent(key).map(Node::newOrCachedInstance)
                .orElseThrow(() -> new IllegalStateException(String.format("No global component with key '%s'  ", key)));
        return (T) ob;
    }

    private Collection<ComponentNode> componentNodes() {
        return nodesOfType(nodes(), ComponentNode.class);
    }

    private Collection<ComponentRegistryNode> componentRegistryNodes() {
        return nodesOfType(nodes(), ComponentRegistryNode.class);
    }

    private Collection<ComponentNode> osgiComponentsOfClass(Class<?> clazz) {
        return componentNodes().stream().filter(node -> clazz.isAssignableFrom(node.componentType())).collect(Collectors.toList());
    }

    public List<Node> complete(Injector fallbackInjector) {
        componentNodes().forEach(node -> completeNode(node, fallbackInjector));
        componentRegistryNodes().forEach(this::completeComponentRegistryNode);
        return topologicalSort(nodes());
    }

    public List<Node> complete() {
        return complete(Guice.createInjector());
    }

    public Set<ConfigKey<? extends ConfigInstance>> configKeys() {
        return nodes().stream().flatMap(node -> node.configKeys().stream()).collect(Collectors.toSet());
    }

    public void setAvailableConfigs(Map<ConfigKey<? extends ConfigInstance>, ConfigInstance> configs) {
        Map<ConfigKey<ConfigInstance>, ConfigInstance> invariantMap = Keys.invariantCopy(configs);
        componentNodes().forEach(node -> node.setAvailableConfigs(invariantMap));
    }

    public void reuseNodes(ComponentGraph old) {
        //  copy instances if node equal
        Set<ComponentId> commonComponentIds = Sets.intersection(nodesById.keySet(), old.nodesById.keySet());
        for (ComponentId id : commonComponentIds) {
            if (nodesById.get(id).equals(old.nodesById.get(id))) {
                nodesById.get(id).instance = old.nodesById.get(id).instance;
            }
        }

        // reset instances with modified dependencies
        for (Node node : topologicalSort(nodes())) {
            for (Node usedComponent : node.usedComponents()) {
                if (!usedComponent.instance.isPresent()) {
                    node.instance = Optional.empty();
                }
            }
        }
    }

    public Collection<?> allComponentsAndProviders() {
        return nodes().stream().map(node -> node.instance().get()).collect(Collectors.toList());
    }

    private void completeComponentRegistryNode(ComponentRegistryNode registry) {
        registry.injectAll(osgiComponentsOfClass(registry.componentClass()));
    }

    private void completeNode(ComponentNode node, Injector fallbackInjector) {
        try {
            Object[] arguments = node.getAnnotatedConstructorParams().stream().map(param -> handleParameter(node, fallbackInjector, param))
                    .toArray();

            node.setArguments(arguments);
        } catch (Exception e) {
            throw removeStackTrace(new RuntimeException("When resolving dependencies of " + node.idAndType(), e));
        }
    }

    private Object handleParameter(Node node, Injector fallbackInjector, Pair<Type, List<Annotation>> annotatedParameterType) {
        Type parameterType = annotatedParameterType.getFirst();
        List<Annotation> annotations = annotatedParameterType.getSecond();

        if (parameterType instanceof Class && parameterType.equals(ComponentId.class)) {
            return node.componentId();
        } else if (parameterType instanceof Class && ConfigInstance.class.isAssignableFrom((Class<?>) parameterType)) {
            return handleConfigParameter((ComponentNode) node, (Class<?>) parameterType);
        } else if (parameterType instanceof ParameterizedType
                && ((ParameterizedType) parameterType).getRawType().equals(ComponentRegistry.class)) {
            ParameterizedType registry = (ParameterizedType) parameterType;
            return getComponentRegistry(registry.getActualTypeArguments()[0]);
        } else if (parameterType instanceof Class) {
            return handleComponentParameter(node, fallbackInjector, (Class<?>) parameterType, annotations);
        } else if (parameterType instanceof ParameterizedType) {
            throw new RuntimeException("Injection of parameterized type " + parameterType + " is not supported.");
        } else {
            throw new RuntimeException("Injection of type " + parameterType + " is not supported");
        }
    }

    private ComponentRegistryNode newComponentRegistryNode(Class<?> componentClass) {
        ComponentRegistryNode registry = new ComponentRegistryNode(componentClass);
        add(registry); //TODO: don't mutate nodes here.
        return registry;
    }

    private ComponentRegistryNode getComponentRegistry(Type componentType) {
        Class<?> componentClass;
        if (componentType instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) componentType;
            if (wildcardType.getLowerBounds().length > 0 || wildcardType.getUpperBounds().length > 1) {
                throw new RuntimeException("Can't create ComponentRegistry of unknown wildcard type" + wildcardType);
            }
            componentClass = (Class<?>) wildcardType.getUpperBounds()[0];
        } else if (componentType instanceof Class) {
            componentClass = (Class<?>) componentType;
        } else if (componentType instanceof TypeVariable) {
            throw new RuntimeException("Can't create ComponentRegistry of unknown type variable " + componentType);
        } else {
            throw new RuntimeException("Can't create ComponentRegistry of unknown type " + componentType);
        }

        for (ComponentRegistryNode node : componentRegistryNodes()) {
            if (node.componentClass().equals(componentType)) {
                return node;
            }
        }
        return newComponentRegistryNode(componentClass);
    }

    @SuppressWarnings("unchecked")
    private ConfigKey<ConfigInstance> handleConfigParameter(ComponentNode node, Class<?> clazz) {
        Class<ConfigInstance> castClass = (Class<ConfigInstance>) clazz;
        return new ConfigKey<>(castClass, node.configId());
    }

    private <T> Key<T> getKey(Class<T> clazz, Optional<Annotation> bindingAnnotation) {
        return bindingAnnotation.map(annotation -> Key.get(clazz, annotation)).orElseGet(() -> Key.get(clazz));
    }

    private Optional<GuiceNode> matchingGuiceNode(Key<?> key, Object instance) {
        return matchingNodes(nodes(), GuiceNode.class, key).stream().filter(node -> node.newOrCachedInstance() == instance). // TODO: assert that there is only one (after filter)
                findFirst();
    }

    private Node lookupOrCreateGlobalComponent(Node node, Injector fallbackInjector, Class<?> clazz, Key<?> key) {
        Optional<Node> component = lookupGlobalComponent(key);
        if (!component.isPresent()) {
            Object instance;
            try {
                log.log(LogLevel.DEBUG, "Trying the fallback injector to create" + messageForNoGlobalComponent(clazz, node));
                instance = fallbackInjector.getInstance(key);
            } catch (ConfigurationException e) {
                throw removeStackTrace(new IllegalStateException(
                        (messageForMultipleClassLoaders(clazz).isEmpty()) ? "No global" + messageForNoGlobalComponent(clazz, node)
                                : messageForMultipleClassLoaders(clazz)));
            }
            component = Optional.of(matchingGuiceNode(key, instance).orElseGet(() -> {
                GuiceNode guiceNode = new GuiceNode(instance, key.getAnnotation());
                add(guiceNode);
                return guiceNode;
            }));
        }
        return component.get();
    }

    private Node handleComponentParameter(Node node, Injector fallbackInjector, Class<?> clazz, Collection<Annotation> annotations) {

        List<Annotation> bindingAnnotations = annotations.stream().filter(ComponentGraph::isBindingAnnotation).collect(Collectors.toList());
        Key<?> key = getKey(clazz, bindingAnnotations.stream().findFirst());

        if (bindingAnnotations.size() > 1) {
            throw new RuntimeException(String.format("More than one binding annotation used in class '%s'", node.instanceType()));
        }

        Collection<ComponentNode> injectedNodesOfCorrectType = matchingComponentNodes(node.componentsToInject, key);
        if (injectedNodesOfCorrectType.size() == 0) {
            return lookupOrCreateGlobalComponent(node, fallbackInjector, clazz, key);
        } else if (injectedNodesOfCorrectType.size() == 1) {
            return Iterables.get(injectedNodesOfCorrectType, 0);
        } else {
            //TODO: !className for last parameter
            throw new RuntimeException(
                    String.format("Multiple components of type '%s' injected into component '%s'", clazz.getName(), node.instanceType()));
        }
    }

    private static String messageForNoGlobalComponent(Class<?> clazz, Node node) {
        return String.format(" component of class %s to inject into component %s.", clazz.getName(), node.idAndType());
    }

    private String messageForMultipleClassLoaders(Class<?> clazz) {
        String errMsg = "Class " + clazz.getName() + " is provided by the framework, and cannot be embedded in a user bundle. "
                + "To resolve this problem, please refer to osgi-classloading.html#multiple-implementations in the documentation";

        try {
            Class<?> resolvedClass = Class.forName(clazz.getName(), false, this.getClass().getClassLoader());
            if (!resolvedClass.equals(clazz)) {
                return errMsg;
            }
        } catch (ClassNotFoundException ignored) {

        }
        return "";
    }

    public static Node getNode(ComponentGraph graph, String componentId) {
        return graph.nodesById.get(new ComponentId(componentId));
    }

    private static <T> Collection<T> nodesOfType(Collection<Node> nodes, Class<T> clazz) {
        List<T> ret = new ArrayList<>();
        for (Node node : nodes) {
            if (clazz.isInstance(node)) {
                ret.add(clazz.cast(node));
            }
        }
        return ret;
    }

    private static Collection<ComponentNode> matchingComponentNodes(Collection<Node> nodes, Key<?> key) {
        return matchingNodes(nodes, ComponentNode.class, key);
    }

    // Finds all nodes with a given nodeType and instance with given key
    private static <T extends Node> Collection<T> matchingNodes(Collection<Node> nodes, Class<T> nodeType, Key<?> key) {
        Class<?> clazz = key.getTypeLiteral().getRawType();
        Annotation annotation = key.getAnnotation();

        List<T> filteredByClass = nodesOfType(nodes, nodeType).stream().filter(node -> clazz.isAssignableFrom(node.componentType()))
                .collect(Collectors.toList());

        if (filteredByClass.size() == 1) {
            return filteredByClass;
        } else {
            List<T> filteredByClassAndAnnotation = filteredByClass.stream()
                    .filter(node -> (annotation == null && node.instanceKey().getAnnotation() == null)
                            || annotation.equals(node.instanceKey().getAnnotation()))
                    .collect(Collectors.toList());
            if (filteredByClassAndAnnotation.size() > 0) {
                return filteredByClassAndAnnotation;
            } else {
                return filteredByClass;
            }
        }
    }

    // Returns true if annotation is a BindingAnnotation, e.g. com.google.inject.name.Named
    public static boolean isBindingAnnotation(Annotation annotation) {
        LinkedList<Class<?>> queue = new LinkedList<>();
        queue.add(annotation.getClass());
        queue.addAll(Arrays.asList(annotation.getClass().getInterfaces()));

        while (!queue.isEmpty()) {
            Class<?> clazz = queue.removeFirst();
            if (clazz.getAnnotation(BindingAnnotation.class) != null) {
                return true;
            } else {
                if (clazz.getSuperclass() != null) {
                    queue.addFirst(clazz.getSuperclass());
                }
            }
        }
        return false;
    }

    /**
     * The returned list is the nodes from the graph bottom-up.
     *
     * @return A list where a earlier than b in the list implies that there is no path from a to b
     */
    private static List<Node> topologicalSort(Collection<Node> nodes) {
        Map<ComponentId, Integer> numIncoming = new HashMap<>();

        nodes.forEach(
                node -> node.usedComponents().forEach(injectedNode -> numIncoming.merge(injectedNode.componentId(), 1, (a, b) -> a + b)));
        LinkedList<Node> sorted = new LinkedList<>();
        List<Node> unsorted = new ArrayList<>(nodes);

        while (!unsorted.isEmpty()) {
            List<Node> ready = new ArrayList<>();
            List<Node> notReady = new ArrayList<>();
            unsorted.forEach(node -> {
                if (numIncoming.getOrDefault(node.componentId(), 0) == 0) {
                    ready.add(node);
                } else {
                    notReady.add(node);
                }
            });

            if (ready.isEmpty()) {
                throw new IllegalStateException("There is a cycle in the component injection graph.");
            }

            ready.forEach(node -> node.usedComponents()
                    .forEach(injectedNode -> numIncoming.merge(injectedNode.componentId(), -1, (a, b) -> a + b)));
            sorted.addAll(0, ready);
            unsorted = notReady;
        }
        return sorted;
    }
}
