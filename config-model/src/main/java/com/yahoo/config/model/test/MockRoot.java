// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.test;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.ConfigModelRepo;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.AbstractConfigProducerRoot;
import com.yahoo.vespa.model.ConfigProducer;
import com.yahoo.vespa.model.HostSystem;
import com.yahoo.vespa.model.admin.Admin;
import com.yahoo.vespa.model.filedistribution.FileDistributionConfigProducer;
import java.util.Collections;
import java.util.Set;


/**
 * Use for testing. Use as parent for the config producer(s) you want to test, to test
 * only a subtree of the producers.
 *
 * @author gjoranv
 */
// TODO: mockRoot instances can probably be replaced by VespaModel.createIncomplete
public class MockRoot extends AbstractConfigProducerRoot {

    private final HostSystem hostSystem;
    private final DeployState deployState;

    public MockRoot() { this(""); }

    public MockRoot(String rootConfigId) {
        this(rootConfigId, new MockApplicationPackage.Builder().build());
    }

    public MockRoot(String rootConfigId, ApplicationPackage applicationPackage) {
        this(rootConfigId, new DeployState.Builder().applicationPackage(applicationPackage).build());
    }

    public MockRoot(String rootConfigId, DeployState deployState) {
        super(rootConfigId);
        hostSystem = new HostSystem(this, "hostsystem", deployState.getProvisioner(), deployState.getDeployLogger(), deployState.isHosted());
        this.deployState = deployState;
    }

    public FileDistributionConfigProducer getFileDistributionConfigProducer() {
        return null;
    }

    @Override
    public ConfigModelRepo configModelRepo() {
        return new ConfigModelRepo();
    }

    public Set<String> getConfigIds() {
        return Collections.unmodifiableSet(id2producer.keySet());
    }

    @Override
    public ConfigInstance.Builder getConfig(ConfigInstance.Builder builder, String configId) {
        ConfigProducer cp = id2producer.get(configId);
        if (cp == null) return null;

        cp.cascadeConfig(builder);
        cp.addUserConfig(builder);
        return builder;
    }

    public <T extends ConfigInstance> T getConfig(Class<T> configClass, String configId) {
        try {
            ConfigInstance.Builder builder = getConfig(getBuilder(configClass).getDeclaredConstructor().newInstance(), configId);
            return configClass.getConstructor(builder.getClass()).newInstance(builder);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public ConfigProducer getProducer(String configId) {
        return id2producer.get(configId);
    }

    @SuppressWarnings("unchecked")
    public static <T extends ConfigInstance> Class<? extends ConfigInstance.Builder> getBuilder(Class<T> configClass) {
        for (Class<?> memberClass : configClass.getClasses()) {
            if (memberClass.getSimpleName().equals("Builder"))
                return (Class<? extends ConfigInstance.Builder>) memberClass;
        }
        throw new RuntimeException("Missing builder");
    }

    public DeployState getDeployState() {
        return deployState;
    }

    public HostSystem hostSystem() { return hostSystem; }

    public void addDescendant(String configId, AnyConfigProducer descendant) {
        if (id2producer.containsKey(configId)) {
            throw new RuntimeException
                    ("Config ID '" + configId + "' cannot be reserved by an instance of class '" +
                            descendant.getClass().getName() + "' since it is already used by an instance of class '" +
                            id2producer.get(configId).getClass().getName() + "'");
        }
        id2producer.put(configId, descendant);
    }

    @Override
    public void addChild(AnyConfigProducer abstractConfigProducer) {
        super.addChild(abstractConfigProducer);
    }

    @Override
    public final Admin getAdmin() { return null; }

    public DeployLogger deployLogger() {
        return deployState.getDeployLogger();
    }
    
}
