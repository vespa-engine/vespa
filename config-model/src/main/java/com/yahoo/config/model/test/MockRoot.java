// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.test;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.ConfigModelRepo;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.model.producer.AbstractConfigProducerRoot;
import com.yahoo.config.provision.Zone;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.ConfigProducer;
import com.yahoo.vespa.model.HostSystem;
import com.yahoo.vespa.model.admin.Admin;
import com.yahoo.vespa.model.builder.xml.dom.DomAdminV2Builder;
import com.yahoo.vespa.model.filedistribution.FileDistributionConfigProducer;
import com.yahoo.vespa.model.filedistribution.FileDistributor;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
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

    private static final long serialVersionUID = 1L;

    private HostSystem hostSystem;

    private final DeployState deployState;
    private FileDistributor fileDistributor;
    private Admin admin;

    public MockRoot() {
        this("");
    }

    public MockRoot(String rootConfigId) {
        this(rootConfigId, new MockApplicationPackage.Builder().build());
    }

    public MockRoot(String rootConfigId, ApplicationPackage applicationPackage) {
        this(rootConfigId, new DeployState.Builder().applicationPackage(applicationPackage).build(true));
    }

    public MockRoot(String rootConfigId, DeployState deployState) {
        super(rootConfigId);
        hostSystem = new HostSystem(this, "hostsystem", deployState.getProvisioner());
        this.deployState = deployState;
        fileDistributor = new FileDistributor(deployState.getFileRegistry(), null);
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

    @SuppressWarnings("unchecked")
    public <T extends ConfigInstance> T getConfig(Class<T> configClass, String configId) {
        try {
            ConfigInstance.Builder builder = getConfig(getBuilder(configClass).newInstance(), configId);
            return configClass.getConstructor(builder.getClass()).newInstance(builder);
        } catch (InstantiationException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
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

    @Override
    public DeployState getDeployState() {
        return deployState;
    }

    public FileDistributor getFileDistributor() {
        return fileDistributor;
    }

    public HostSystem getHostSystem() {
        return hostSystem;
    }

    public void addDescendant(String configId, AbstractConfigProducer descendant) {
        if (id2producer.containsKey(configId)) {
            throw new RuntimeException
                    ("Config ID '" + configId + "' cannot be reserved by an instance of class '" +
                            descendant.getClass().getName() + "' since it is already used by an instance of class '" +
                            id2producer.get(configId).getClass().getName() + "'");
        }
        id2producer.put(configId, descendant);
    }

    @Override
    public void addChild(AbstractConfigProducer abstractConfigProducer) {
        super.addChild(abstractConfigProducer);
    }

    public final void setAdmin(String xml) {
        String servicesXml =
                "<?xml version='1.0' encoding='utf-8' ?>" +
                "<services>" + xml + "</services>";

        try {
            Document doc = XmlHelper.getDocumentBuilder().parse(new InputSource(new StringReader(servicesXml)));
            setAdmin(new DomAdminV2Builder(ConfigModelContext.ApplicationType.DEFAULT, deployState.getFileRegistry(),
                                           false, new ArrayList<>()).
                    build(this, XML.getChildren(doc.getDocumentElement(), "admin").get(0)));
        } catch (SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public final void setAdmin(Admin admin) {
        this.admin = admin;
    }

    @Override
    public final Admin getAdmin() {
        return admin;
    }

    @Override
    public DeployLogger deployLogger() {
        return new BaseDeployLogger();
    }
    
}
