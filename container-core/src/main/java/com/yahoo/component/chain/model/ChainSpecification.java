// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.chain.model;

import com.google.common.collect.ImmutableSet;
import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.Phase;

import java.util.*;

/**
 * Specifies how the components should be selected to create a chain. Immutable.
 *
 * @author Tony Vaagenes
 */
public class ChainSpecification {

    public static class Inheritance {
        public final Set<ComponentSpecification> chainSpecifications;
        public final Set<ComponentSpecification> excludedComponents;

        Inheritance flattened() {
            return new Inheritance(Collections.<ComponentSpecification>emptySet(), excludedComponents);
        }

        public Inheritance(Set<ComponentSpecification> inheritedChains, Set<ComponentSpecification> excludedComponents) {
            this.chainSpecifications = immutableCopy(inheritedChains);
            this.excludedComponents = immutableCopy(excludedComponents);
        }

        public Inheritance addInherits(Collection<ComponentSpecification> inheritedChains) {
            Set<ComponentSpecification> newChainSpecifications =
                    new LinkedHashSet<>(chainSpecifications);
            newChainSpecifications.addAll(inheritedChains);
            return new Inheritance(newChainSpecifications, excludedComponents);
        }
    }

    public final ComponentId componentId;
    public final Inheritance inheritance;
    final Map<String, Phase> phases;
    public final Set<ComponentSpecification> componentReferences;

    public ChainSpecification(ComponentId componentId, Inheritance inheritance,
                              Collection<Phase> phases,
                              Set<ComponentSpecification> componentReferences) {
        assertNotNull(componentId, inheritance, phases, componentReferences);

        if (componentsByName(componentReferences).size() != componentReferences.size())
            throw new RuntimeException("Two components with the same name are specified in '" + componentId +
                    "', but name must be unique inside a given chain.");

        this.componentId = componentId;
        this.inheritance = inheritance;
        this.phases = copyPhasesImmutable(phases);
        this.componentReferences = ImmutableSet.copyOf(
                filterByComponentSpecification(componentReferences, inheritance.excludedComponents));
    }

    public ChainSpecification addComponents(Collection<ComponentSpecification> componentSpecifications) {
        Set<ComponentSpecification> newComponentReferences = new LinkedHashSet<>(componentReferences);
        newComponentReferences.addAll(componentSpecifications);

        return new ChainSpecification(componentId, inheritance, phases(), newComponentReferences);
    }

    public ChainSpecification addInherits(Collection<ComponentSpecification> inheritedChains) {
        return new ChainSpecification(componentId, inheritance.addInherits(inheritedChains), phases(), componentReferences);
    }

    public ChainSpecification setComponentId(ComponentId newComponentId) {
        return new ChainSpecification(newComponentId, inheritance, phases(), componentReferences);
    }

    public ChainSpecification flatten(Resolver<ChainSpecification> allChainSpecifications) {
        Deque<ComponentId> path = new ArrayDeque<>();
        return flatten(allChainSpecifications, path);
    }

    /**
     * @param allChainSpecifications resolves ChainSpecifications from ComponentSpecifications
     *                               as given in the inheritance fields.
     * @param path tracks which chains are used in each recursive invocation of flatten, used for detecting cycles.
     * @return ChainSpecification directly containing all the component references and phases of the inherited chains.
     */
    private ChainSpecification flatten(Resolver<ChainSpecification> allChainSpecifications,
                                             Deque<ComponentId> path) {
        path.push(componentId);

        //if this turns out to be a bottleneck(which I seriously doubt), please add memoization
        Map<String, ComponentSpecification> resultingComponents = componentsByName(componentReferences);
        Map<String, Phase> resultingPhases = new LinkedHashMap<>(phases);


        for (ComponentSpecification inheritedChainSpecification : inheritance.chainSpecifications) {
            ChainSpecification inheritedChain =
                    resolveChain(path, allChainSpecifications, inheritedChainSpecification).
                            flatten(allChainSpecifications, path);

            mergeInto(resultingComponents,
                    filterByComponentSpecification(
                            filterByName(inheritedChain.componentReferences, names(componentReferences)),
                            inheritance.excludedComponents));
            mergeInto(resultingPhases, inheritedChain.phases);
        }

        path.pop();
        return new ChainSpecification(componentId, inheritance.flattened(), resultingPhases.values(),
                new LinkedHashSet<>(resultingComponents.values()));
    }

    public Collection<Phase> phases() {
        return phases.values();
    }

    private static <T> Set<T> immutableCopy(Set<T> set) {
        if (set == null) return ImmutableSet.of();
        return ImmutableSet.copyOf(set);
    }

    private static Map<String, Phase> copyPhasesImmutable(Collection<Phase> phases) {
        Map<String, Phase> result = new LinkedHashMap<>();
        for (Phase phase : phases) {
            Phase oldValue = result.put(phase.getName(), phase);
            if (oldValue != null)
                throw new RuntimeException("Two phases with the same name " + phase.getName() + " present in the same scope.");
        }
        return Collections.unmodifiableMap(result);
    }

    private static void assertNotNull(Object... objects) {
        for (Object o : objects) {
            assert(o != null);
        }
    }

    static Map<String, ComponentSpecification> componentsByName(Set<ComponentSpecification> componentSpecifications) {
        Map<String, ComponentSpecification> componentsByName = new LinkedHashMap<>();

        for (ComponentSpecification component : componentSpecifications)
            componentsByName.put(component.getName(), component);

        return componentsByName;
    }

    private static void mergeInto(Map<String, ComponentSpecification> resultingComponents,
                                  Set<ComponentSpecification> components) {
        for (ComponentSpecification component : components) {
            String name = component.getName();
            if (resultingComponents.containsKey(name)) {
                resultingComponents.put(name, component.intersect(resultingComponents.get(name)));
            } else {
                resultingComponents.put(name, component);
            }
        }
    }


    private static void mergeInto(Map<String, Phase> resultingPhases, Map<String, Phase> phases) {
        for (Phase phase : phases.values()) {
            String name = phase.getName();
            if (resultingPhases.containsKey(name)) {
                phase = phase.union(resultingPhases.get(name));
            }
            resultingPhases.put(name, phase);
        }
    }

    private static Set<String> names(Set<ComponentSpecification> components) {
        Set<String> names = new LinkedHashSet<>();
        for (ComponentSpecification component : components) {
            names.add(component.getName());
        }
        return names;
    }

    private static Set<ComponentSpecification> filterByComponentSpecification(Set<ComponentSpecification> components, Set<ComponentSpecification> excludes) {
        Set<ComponentSpecification> result = new LinkedHashSet<>();
        for (ComponentSpecification component : components) {
            if (!matches(component, excludes))
                result.add(component);
        }

        return result;
    }

    private static Set<ComponentSpecification> filterByName(Set<ComponentSpecification> components, Set<String> names) {
        Set<ComponentSpecification> result = new LinkedHashSet<>();
        for (ComponentSpecification component : components) {
            if (!names.contains(component.getName()))
                result.add(component);
        }
        return result;
    }

    private static boolean matches(ComponentSpecification component, Set<ComponentSpecification> excludes) {
        ComponentId id = component.toId().withoutNamespace();
        for (ComponentSpecification exclude : excludes) {
            if (exclude.matches(id)) {
                return true;
            }
        }
        return false;
    }

    private ChainSpecification resolveChain(Deque<ComponentId> path,
                                            Resolver<ChainSpecification> allChainSpecifications,
                                            ComponentSpecification chainSpecification) {

        ChainSpecification chain = allChainSpecifications.resolve(chainSpecification);
        if (chain == null) {
            throw new RuntimeException("Missing chain '" + chainSpecification + "'.");
        } else if (path.contains(chain.componentId)) {
            throw new RuntimeException("The chain " + chain.componentId + " inherits(possibly indirectly) from itself.");
        } else {
            return chain;
        }
    }

}
