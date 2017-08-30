// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.chain.dependencies.ordering;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.chain.ChainedComponent;
import com.yahoo.component.chain.Phase;


/**
 * Given a set of phases and a set of components,
 * a ordered list of components satisfying the dependencies is given if possible.
 * <p>
 * The phase list implicitly defines the ordering:
 *     {@literal if i  < j : p_i before p_j  where i,j are valid indexes of the phrase list p.}
 * <p>
 * If multiple components provide the same name, ALL the components providing
 * the same name must be placed earlier/later than an entity depending on
 * that name.
 * <p>
 * A warning will be logged if multiple components of different types provides the
 * same name. A component can not provide the same name as a phase.
 *
 * @author Tony Vaagenes
 */
public class ChainBuilder<T extends ChainedComponent> {

    private final ComponentId id;
    private int numComponents = 0;
    private int priority = 1;

    private Map<String, NameProvider> nameProviders =
            new LinkedHashMap<>();

    private Node allPhase;

    public ChainBuilder(ComponentId id) {
        this.id = id;
        allPhase = addPhase(new Phase("*", set("*"), Collections.<String>emptySet()));
    }

    private Set<String> set(String... s) {
        return new HashSet<>(Arrays.asList(s));
    }

    public PhaseNameProvider addPhase(Phase phase) {
        NameProvider nameProvider = nameProviders.get(phase.getName());
        if (nameProvider instanceof ComponentNameProvider) {
            throw new ConflictingNodeTypeException("Cannot add phase '" + phase.getName() + "' as it is already provided by " + nameProvider);
        }
        PhaseNameProvider phaseNameProvider;
        if(nameProvider == null) {
            phaseNameProvider = new PhaseNameProvider(phase.getName(), priority++);
        } else {
            phaseNameProvider = (PhaseNameProvider) nameProvider;
        }
        nameProviders.put(phase.getName(), phaseNameProvider);
        for(String before : phase.before()) {
            phaseNameProvider.before(getPhaseNameProvider(before));
        }
        for(String after : phase.after()) {
            getPhaseNameProvider(after).before(phaseNameProvider);
        }

        return phaseNameProvider;
    }

    public void addComponent(ChainedComponent component) {
        ComponentNode<ChainedComponent> componentNode = new ComponentNode<>(component, priority++);

        ensureProvidesNotEmpty(component);
        for (String name : component.getDependencies().provides()) {
            NameProvider nameProvider = getNameProvider(name);

            nameProvider.addNode(componentNode);
        }

        for (String before : component.getDependencies().before()) {
            componentNode.before(getNameProvider(before));
        }

        for (String after : component.getDependencies().after()) {
            getNameProvider(after).before(componentNode);
        }

        ++numComponents;
    }

    //destroys this dependency handler in the process
    @SuppressWarnings("unchecked")
    public Chain<T> orderNodes() {
        List<T> chain = new ArrayList<>();
        OrderedReadyNodes readyNodes = getReadyNodes();

        while (!readyNodes.isEmpty() || popAllPhase(readyNodes) ) {
            Node candidate = readyNodes.pop();

            candidate.removed(readyNodes);

            if ( candidate instanceof ComponentNode)
                chain.add(((ComponentNode<T>)candidate).getComponent());
        }

        if (  chain.size() != numComponents)
            throw new CycleDependenciesException(nameProviders);

        //prevent accidental reuse
        nameProviders = null;

        return new Chain<>(id, chain);
    }

    private void ensureProvidesNotEmpty(ChainedComponent component) {
        if (component.getDependencies().provides().isEmpty()) {
            throw new RuntimeException("The component " + component.getId() + " did not provide anything.");
        }
    }

    private Node getPhaseNameProvider(String name) {
        NameProvider nameProvider = nameProviders.get(name);
        if (nameProvider != null)
            return nameProvider;
        else {
            nameProvider = new PhaseNameProvider(name, priority++);
            nameProviders.put(name, nameProvider);
            return nameProvider;
        }
    }

    private boolean popAllPhase(OrderedReadyNodes readyNodes) {
        if (allPhase == null) {
            return false;
        } else {
            Node phase = allPhase;
            allPhase = null;
            phase.removed(readyNodes);
            return !readyNodes.isEmpty();
        }
    }

    private NameProvider getNameProvider(String name) {
        NameProvider nameProvider = nameProviders.get(name);
        if (nameProvider != null)
            return nameProvider;
        else {
            nameProvider = new ComponentNameProvider(name);
            nameProviders.put(name, nameProvider);
            return nameProvider;
        }
    }

    private OrderedReadyNodes getReadyNodes() {
        OrderedReadyNodes readyNodes = new OrderedReadyNodes();
        for (Node node : nameProviders.values() ) {
            if (node.ready())
                readyNodes.add(node);
        }
        return readyNodes;
    }

}
