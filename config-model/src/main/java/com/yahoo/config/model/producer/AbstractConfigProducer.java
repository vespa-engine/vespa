// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.producer;

import com.yahoo.api.annotations.Beta;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.model.ApplicationConfigProducerRoot;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.subscription.ConfigInstanceUtil;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.ConfigPayloadBuilder;
import com.yahoo.vespa.config.ConfigTransformer;
import com.yahoo.vespa.config.GenericConfig;
import com.yahoo.vespa.model.ConfigProducer;
import com.yahoo.vespa.model.HostSystem;
import com.yahoo.vespa.model.Service;
import com.yahoo.vespa.model.SimpleConfigProducer;
import com.yahoo.vespa.model.admin.Admin;
import com.yahoo.vespa.model.admin.monitoring.Monitoring;
import com.yahoo.vespa.model.utils.FreezableMap;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Superclass for all config producers.
 * Config producers constructs and returns config instances on request.
 *
 * @author gjoranv
 */
public abstract class AbstractConfigProducer<CHILD extends AbstractConfigProducer<?>>
        implements ConfigProducer, ConfigInstance.Producer, Serializable {

    private static final long serialVersionUID = 1L;
    public static final Logger log = Logger.getLogger(AbstractConfigProducer.class.getPackage().toString());
    private final String subId;
    private String configId = null;

    private final List<Service> descendantServices = new ArrayList<>();

    private AbstractConfigProducer parent = null;

    private UserConfigRepo userConfigs = new UserConfigRepo();

    private final FreezableMap<String, CHILD> childrenBySubId = new FreezableMap<>(LinkedHashMap.class);

    protected static boolean stateIsHosted(DeployState deployState) {
        return (deployState != null) && deployState.isHosted();
    }

    /**
     * Creates a new AbstractConfigProducer with the given parent and subId.
     * This constructor will add the resulting producer to the children of parent.
     *
     * @param parent the parent of this ConfigProducer
     * @param subId  the fragment of the config id for the producer
     */
    public AbstractConfigProducer(AbstractConfigProducer parent, String subId) {
        this(subId);
        if (parent != null) {
            parent.addChild(this);
        }
    }

    /** Removes this from the config model */
    protected void remove() {
        if (parent != null)
            parent.removeChild(this);
    }

    protected final void setParent(AbstractConfigProducer<?> parent) {
        this.parent = parent;
        computeConfigId();
    }

    public final String getSubId() { return subId; }

    /**
     * Create an config producer with a configId only. Used e.g. to create root nodes, and producers
     * that are given children after construction using {@link #addChild(AbstractConfigProducer)}.
     *
     * @param subId The sub configId. Note that this can be prefixed when calling addChild with this producer as arg.
     */
    public AbstractConfigProducer(String subId) {
        if (subId.indexOf('/') != -1) {
            throw new IllegalArgumentException("A subId might not contain '/' : '" + subId + "'");
        }
        this.subId = subId;
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
                                               errorMsgClassName() + " '" + subId + "'. (This is commonly caused by service/node index " +
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

    /**
     * Helper to provide an error message on collisions of sub ids (ignore SimpleConfigProducer, use the parent in that case)
     */
    private String errorMsgClassName() {
        if (getClass().equals(SimpleConfigProducer.class)) return parent.getClass().getSimpleName();
        return getClass().getSimpleName();
    }

    /**
     * Sets the user configs for this producer.
     *
     * @param repo User configs repo.
     */
    public void setUserConfigs(UserConfigRepo repo) { this.userConfigs = repo; }

    /** Returns the user configs of this */
    @Override
    public UserConfigRepo getUserConfigs() { return userConfigs; }

    /**
     * ConfigProducers that must have a special config id should use
     * setConfigId() instead of overloading this method.  This is
     * because config IDs must be registered through setConfigId().
     */
    public final String getConfigId() {
        if (configId == null) throw new IllegalStateException("The system topology must be frozen first.");
        return configId;
    }

    /**
     * Sets the config id for this producer. Will also add this
     * service to the root node, so the new config id will be picked
     * up.  Note that this producer will be known with both the old
     * and the new config id in the root node after using this method.
     */
    protected void addConfigId(String id) {
        if (id == null) throw new NullPointerException("Config ID cannot be null.");
        getRoot().addDescendant(id, this);
        if (!isVespa() && (getVespa() != null))
            getVespa().addDescendant(this);
    }

    /** Returns this ConfigProducer's children (only 1st level) */
    public Map<String, CHILD> getChildren() { return Collections.unmodifiableMap(childrenBySubId); }

    @Beta
    public <J extends AbstractConfigProducer<?>> List<J> getChildrenByTypeRecursive(Class<J> type) {
        List<J> validChildren = new ArrayList<>();

        if (this.getClass().equals(type)) {
            validChildren.add(type.cast(this));
        }

        Map<String, ? extends AbstractConfigProducer<?>> children = this.getChildren();
        for (AbstractConfigProducer<?> child : children.values()) {
            validChildren.addAll(child.getChildrenByTypeRecursive(type));
        }

        return Collections.unmodifiableList(validChildren);
    }

    /** Returns a list of all the children of this who are instances of Service */
    public List<Service> getDescendantServices() { return Collections.unmodifiableList(descendantServices); }

    protected void addDescendantService(Service s) { descendantServices.add(s); }

    @Override
    public final boolean cascadeConfig(ConfigInstance.Builder builder) {
        boolean found = false;
        if (parent != null)
            found = parent.cascadeConfig(builder);

        boolean foundHere = builder.dispatchGetConfig(this);
        log.log(Level.FINE, () -> "cascadeconfig in " + this + ", getting config " +
                                  builder.getClass().getDeclaringClass().getName() + " for config id '" + configId +
                                  "' found here=" + foundHere);
        found = found || foundHere;
        return found;
    }

    @Override
    public final boolean addUserConfig(ConfigInstance.Builder builder) {
        boolean didApply = false;
        if (parent != null) {
            didApply = parent.addUserConfig(builder);
        }

        log.log(Level.FINEST, () -> "User configs is: " + userConfigs.toString());
        // TODO: What do we do with md5. Currently ignored for user configs?
        ConfigDefinitionKey key = new ConfigDefinitionKey(builder.getDefName(), builder.getDefNamespace());
        if (userConfigs.get(key) != null) {
            log.log(Level.FINEST, () -> "Apply in " + configId);
            applyUserConfig(builder, userConfigs.get(key));
            didApply = true;
        }
        return didApply;
    }

    private void applyUserConfig(ConfigInstance.Builder builder, ConfigPayloadBuilder payloadBuilder) {
        ConfigInstance.Builder override;
        if (builder instanceof GenericConfig.GenericConfigBuilder) {
            // Means that the builder is unknown and that we should try to apply the payload without
            // the real builder
            override = getGenericConfigBuilderOverride((GenericConfig.GenericConfigBuilder) builder, payloadBuilder);
        } else {
            override = getConfigInstanceBuilderOverride(builder, ConfigPayload.fromBuilder(payloadBuilder));
        }
        ConfigInstanceUtil.setValues(builder, override);
    }

    private ConfigInstance.Builder getGenericConfigBuilderOverride(GenericConfig.GenericConfigBuilder builder, ConfigPayloadBuilder payloadBuilder) {
        ConfigDefinitionKey key = new ConfigDefinitionKey(builder.getDefName(), builder.getDefNamespace());
        return new GenericConfig.GenericConfigBuilder(key, payloadBuilder);
    }

    private ConfigInstance.Builder getConfigInstanceBuilderOverride(ConfigInstance.Builder builder, ConfigPayload payload) {
        try {
            ConfigTransformer transformer = new ConfigTransformer(builder.getClass().getEnclosingClass());
            return transformer.toConfigBuilder(payload);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Error applying override to builder", e);
        }
    }

    /** Returns the one and only HostSystem of the root node. Must be overridden by root node. */
    public HostSystem hostSystem() { return getRoot().hostSystem(); }

    public AbstractConfigProducerRoot getRoot() {
        return parent == null ? null : parent.getRoot();
    }

    /**
     * Returns the {@link ApplicationConfigProducerRoot} that is the parent of this sub-tree, or null
     * if this sub-tree has no Vespa parent.
     */
    private ApplicationConfigProducerRoot getVespa() {
        if (isRoot()) return null;
        return isVespa() ? (ApplicationConfigProducerRoot)this : parent.getVespa();
    }

    private boolean isRoot() {
        return parent == null;
    }

    private boolean isVespa() {
       return ((this instanceof ApplicationConfigProducerRoot) && parent.isRoot());
    }

    public AbstractConfigProducer getParent() { return parent; }

    public void dump(PrintStream out) {
        for (ConfigProducer c : getChildren().values()) {
            out.println("id: " + c.getConfigId());
            if (c.getChildren().size() > 0) {
                c.dump(out);
            }
        }
    }

    void setupConfigId(String parentConfigId) {
        if (this instanceof AbstractConfigProducerRoot) {
            configId = "";
        } else {
            configId = parentConfigId + subId;
            addConfigId(configId);
        }
        computeConfigId();
        setupChildConfigIds(getConfigIdPrefix());
    }

    private String getConfigIdPrefix() {
        if (this instanceof AbstractConfigProducerRoot || this instanceof ApplicationConfigProducerRoot) {
            return "";
        }
        if (configId == null) return null;
        return configId + "/";
    }

    private void computeConfigId() {
        if (parent == null) return;
        String parentConfigId = parent.getConfigIdPrefix();
        if (parentConfigId == null) return;
        String oldConfigId = configId;
        if (this instanceof AbstractConfigProducerRoot) {
            configId = "";
        } else {
            configId = parentConfigId + subId;
        }
        if (oldConfigId == null) return;
        if (!configId.equals(oldConfigId)) {
            throw new IllegalArgumentException("configId cannot change "+oldConfigId+" -> "+configId+" (invalid topology change)");
        }
    }

    private static ClassLoader findInheritedClassLoader(Class clazz, String producerName) {
        Class<?>[] interfazes = clazz.getInterfaces();
        for (Class interfaze : interfazes) {
            if (producerName.equals(interfaze.getName())) {
                return interfaze.getClassLoader();
            }
        }
        if (clazz.getSuperclass() == null)
            return null;
        return findInheritedClassLoader(clazz.getSuperclass(), producerName);
    }

    protected ClassLoader getConfigClassLoader(String producerName) {
        ClassLoader classLoader = findInheritedClassLoader(getClass(), producerName);
        if (classLoader != null)
            return classLoader;

        // TODO: Make logic correct, so that the deepest child will be the one winning.
        for (AbstractConfigProducer child : childrenBySubId.values()) {
            ClassLoader loader = child.getConfigClassLoader(producerName);
            if (loader != null) {
                return loader;
            }
        }
        return null;
    }

    private void setupChildConfigIds(String currentConfigId) {
        for (AbstractConfigProducer child : childrenBySubId.values()) {
            child.setupConfigId(currentConfigId);
        }
    }

    void aggregateDescendantServices() {
        for (AbstractConfigProducer child : childrenBySubId.values()) {
            child.aggregateDescendantServices();
            descendantServices.addAll(child.descendantServices);
        }
    }

    void freeze() {
        childrenBySubId.freeze();
        for (AbstractConfigProducer child : childrenBySubId.values()) {
            child.freeze();
        }
    }

    public void mergeUserConfigs(UserConfigRepo newRepo) {
        userConfigs.merge(newRepo);
    }

    @Override
    public void validate() throws Exception {
        assert (childrenBySubId.isFrozen());

        for (AbstractConfigProducer<?> child : childrenBySubId.values()) {
            child.validate();
        }
    }

    // TODO: Make producers depend on AdminModel instead
    /** Returns a monitoring service if configured, null otherwise */
    protected Monitoring getMonitoringService() {
        AbstractConfigProducerRoot root = getRoot();
        Admin admin = (root == null? null : root.getAdmin());
        if (admin == null) {
            return null;
        }
        if (admin.getMonitoring() != null) {
            return admin.getMonitoring();
        }
        return null;
    }
}
