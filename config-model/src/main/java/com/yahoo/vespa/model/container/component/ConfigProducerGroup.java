// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.component.ComponentId;
import com.yahoo.config.model.producer.AbstractConfigProducer;

import java.util.*;

/**
 * A group of config producers that have a component id.
 *
 * @author Tony Vaagenes
 */
public class ConfigProducerGroup<CHILD extends AbstractConfigProducer<?>> extends AbstractConfigProducer<CHILD> {

    private final Map<ComponentId, CHILD> producerById = new LinkedHashMap<>();

    public ConfigProducerGroup(AbstractConfigProducer parent, String subId) {
        super(parent, subId);
    }

    public void addComponent(ComponentId id, CHILD producer) {
        boolean wasAdded = producerById.put(id, producer) == null;
        if (!wasAdded) {
            throw new IllegalArgumentException("Two entities have the same component id '" +
                                               id + "' in the same scope.");
        }
        addChild(producer);
    }

    /**
     * Removes a component by id
     *
     * @return the removed component, or null if it was not present
     */
    public CHILD removeComponent(ComponentId componentId) {
        CHILD component = producerById.remove(componentId);
        if (component == null) return null;
        removeChild(component);
        return component;
    }

    public Collection<CHILD> getComponents() {
        return Collections.unmodifiableCollection(getChildren().values());
    }

    public <T extends CHILD> Collection<T> getComponents(Class<T> componentClass) {
        Collection<T> result = new ArrayList<>();

        for (CHILD child: getChildren().values()) {
            if (componentClass.isInstance(child)) {
                result.add(componentClass.cast(child));
            }
        }
        return Collections.unmodifiableCollection(result);
    }

    /**
     * @return A map of all components in this group, with (local) component ID as key.
     */
    public Map<ComponentId, CHILD> getComponentMap() {
        return Collections.unmodifiableMap(producerById);
    }

}
