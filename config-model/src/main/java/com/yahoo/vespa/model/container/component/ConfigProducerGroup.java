// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.component.ComponentId;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * A group of config producers that have a component id.
 *
 * @author Tony Vaagenes
 */
public class ConfigProducerGroup<CHILD extends AnyConfigProducer> extends TreeConfigProducer<CHILD> {

    private final Map<ComponentId, CHILD> producerById = new LinkedHashMap<>();

    public ConfigProducerGroup(TreeConfigProducer<? super ConfigProducerGroup> parent, String subId) {
        super(parent, subId);
    }

    public void addComponent(ComponentId id, CHILD producer) {
        CHILD existing = producerById.put(id, producer);
        if ( existing != null) {
            throw new IllegalArgumentException("Both " + producer + " and " + existing + " are configured" +
                                               " with the id '" + id + "'. All components must have a unique id.");
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

    /** Returns a map of all components in this group, with (local) component ID as key. */
    public Map<ComponentId, CHILD> getComponentMap() {
        return Collections.unmodifiableMap(producerById);
    }

}
