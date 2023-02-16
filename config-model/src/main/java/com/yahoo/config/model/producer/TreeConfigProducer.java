// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.producer;

import com.yahoo.api.annotations.Beta;
import com.yahoo.config.model.ApplicationConfigProducerRoot;
import com.yahoo.vespa.model.Service;
import com.yahoo.vespa.model.SimpleConfigProducer;
import com.yahoo.vespa.model.utils.FreezableMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Superclass for all producers with children.
 * Config producers constructs and returns config instances on request.
 *
 * @author gjoranv
 * @author arnej
 */
public abstract class TreeConfigProducer<CHILD extends AnyConfigProducer>
    extends AnyConfigProducer
{
    private static final long serialVersionUID = 1L;
    private final List<Service> descendantServices = new ArrayList<>();
    private final FreezableMap<String, CHILD> childrenBySubId = new FreezableMap<>(LinkedHashMap.class);

    /**
     * Creates a new TreeConfigProducer with the given parent and subId.
     * This constructor will add the resulting producer to the children of parent.
     *
     * @param parent the parent of this ConfigProducer
     * @param subId  the fragment of the config id for the producer
     */
    public TreeConfigProducer(TreeConfigProducer parent, String subId) {
        super(parent, subId);
    }

    /**
     * Create an config producer with a configId only. Used e.g. to create root nodes, and producers
     * that are given children after construction using {@link #addChild(AnyConfigProducer)}.
     *
     * @param subId The sub configId. Note that this can be prefixed when calling addChild with this producer as arg.
     */
    public TreeConfigProducer(String subId) {
        super(subId);
    }

    /**
     * Helper to provide an error message on collisions of sub ids (ignore SimpleConfigProducer, use the parent in that case)
     */
    private String errorMsgClassName() {
        if (getClass().equals(SimpleConfigProducer.class)) return getParent().getClass().getSimpleName();
        return getClass().getSimpleName();
    }

    /**
     * Adds a child to this config producer.
     *
     * @param child the child config producer to add
     */
    protected void addChild(CHILD child) {
        if (child == null) {
            throw new IllegalArgumentException("Trying to add null child for: " + this);
        }
        if (child instanceof AbstractConfigProducerRoot) {
            throw new IllegalArgumentException("Child cannot be a root node: " + child);
        }

        child.setParent(this);
        if (childrenBySubId.get(child.getSubId()) != null) {
            throw new IllegalArgumentException("Multiple services/instances of the id '" + child.getSubId() + "' under the service/instance " +
                                               errorMsgClassName() + " '" + getSubId() + "'. (This is commonly caused by service/node index " +
                                               "collisions in the config.)." +
                                               "\nExisting instance: " + childrenBySubId.get(child.getSubId()) +
                                               "\nAttempted to add:  " + child);
        }
        childrenBySubId.put(child.getSubId(), child);

        if (child instanceof Service) {
            addDescendantService((Service)child);
        }
    }

    public void removeChild(CHILD child) {
        if (child.getParent() != this)
            throw new IllegalArgumentException("Could not remove " + child  + ": Expected its parent to be " +
                                               this + ", but was " + child.getParent());

        if (child instanceof Service)
            descendantServices.remove(child);

        childrenBySubId.remove(child.getSubId());
        child.setParent(null);
    }

    /** Returns this ConfigProducer's children (only 1st level) */
    public Map<String, CHILD> getChildren() { return Collections.unmodifiableMap(childrenBySubId); }

    @Beta
    public <J extends AnyConfigProducer> List<J> getChildrenByTypeRecursive(Class<J> type) {
        List<J> validChildren = new ArrayList<>();

        if (this.getClass().equals(type)) {
            validChildren.add(type.cast(this));
        }

        Map<String, CHILD> children = this.getChildren();
        for (CHILD child : children.values()) {
            validChildren.addAll(child.getChildrenByTypeRecursive(type));
        }

        return Collections.unmodifiableList(validChildren);
    }

    /** Returns a list of all the children of this who are instances of Service */
    public List<Service> getDescendantServices() { return Collections.unmodifiableList(descendantServices); }

    protected void addDescendantService(Service s) { descendantServices.add(s); }

    void setupConfigId(String parentConfigId) {
        super.setupConfigId(parentConfigId);
        setupChildConfigIds(getConfigIdPrefix());
    }

    String getConfigIdPrefix() {
        if (this instanceof AbstractConfigProducerRoot || this instanceof ApplicationConfigProducerRoot) {
            return "";
        }
        if (currentConfigId() == null) {
            return null;
        }
        return getConfigId() + "/";
    }

    @Override
    protected ClassLoader getConfigClassLoader(String producerName) {
        ClassLoader classLoader = findInheritedClassLoader(getClass(), producerName);
        if (classLoader != null)
            return classLoader;

        // TODO: Make logic correct, so that the deepest child will be the one winning.
        for (AnyConfigProducer child : childrenBySubId.values()) {
            ClassLoader loader = child.getConfigClassLoader(producerName);
            if (loader != null) {
                return loader;
            }
        }
        return null;
    }

    private void setupChildConfigIds(String currentConfigId) {
        for (AnyConfigProducer child : childrenBySubId.values()) {
            child.setupConfigId(currentConfigId);
        }
    }

    @Override
    void aggregateDescendantServices() {
        for (CHILD child : childrenBySubId.values()) {
            child.aggregateDescendantServices();
            descendantServices.addAll(child.getDescendantServices());
        }
    }

    @Override
    void freeze() {
        childrenBySubId.freeze();
        for (CHILD child : childrenBySubId.values()) {
            child.freeze();
        }
    }

    @Override
    public void validate() throws Exception {
        assert (childrenBySubId.isFrozen());
        for (CHILD child : childrenBySubId.values()) {
            child.validate();
        }
    }

}
