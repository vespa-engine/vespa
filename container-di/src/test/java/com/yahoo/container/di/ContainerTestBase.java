// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di;

import com.google.inject.Guice;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.FileReference;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.di.CloudSubscriberFactory;
import com.yahoo.container.di.ContainerTest.ComponentTakingConfig;
import com.yahoo.container.di.componentgraph.core.ComponentGraph;
import com.yahoo.container.di.osgi.BundleClasses;
import org.junit.After;
import org.junit.Before;
import org.osgi.framework.Bundle;

import java.util.Collection;
import java.util.Set;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 * @author ollivir
 */
public class ContainerTestBase {
    private ComponentGraph componentGraph;
    protected DirConfigSource dirConfigSource = null;

    @Before
    public void setup() {
        dirConfigSource = new DirConfigSource("ContainerTest-");
    }

    @After
    public void cleanup() {
        dirConfigSource.cleanup();
    }

    @Before
    public void createGraph() {
        componentGraph = new ComponentGraph(0);
    }

    public void complete() {
        try {
            Container container = new Container(new CloudSubscriberFactory(dirConfigSource.configSource()), dirConfigSource.configId(),
                    new ContainerTest.TestDeconstructor(), new Osgi() {
                        @SuppressWarnings("unchecked")
                        @Override
                        public Class<Object> resolveClass(BundleInstantiationSpecification spec) {
                            try {
                                return (Class<Object>) Class.forName(spec.classId.getName());
                            } catch (ClassNotFoundException e) {
                                throw new RuntimeException(e);
                            }
                        }

                        @Override
                        public BundleClasses getBundleClasses(ComponentSpecification bundle, Set<String> packagesToScan) {
                            throw new UnsupportedOperationException("getBundleClasses not supported");
                        }

                        @Override
                        public void useBundles(Collection<FileReference> bundles) {
                        }

                        @Override
                        public Bundle getBundle(ComponentSpecification spec) {
                            throw new UnsupportedOperationException("getBundle not supported.");
                        }
                    });
            componentGraph = container.getNewComponentGraph(componentGraph, Guice.createInjector(), false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T getInstance(Class<T> componentClass) {
        return componentGraph.getInstance(componentClass);
    }

    protected void writeBootstrapConfigs(ComponentEntry... componentEntries) {
        dirConfigSource.writeConfig("bundles", "");
        StringBuilder components = new StringBuilder();
        for (int i = 0; i < componentEntries.length; i++) {
            components.append(componentEntries[i].asConfig(i));
            components.append('\n');
        }
        dirConfigSource.writeConfig("components", String.format("components[%s]\n%s", componentEntries.length, components));
    }

    protected void writeBootstrapConfigs(String componentId, Class<?> classId) {
        writeBootstrapConfigs(new ComponentEntry(componentId, classId));
    }

    protected void writeBootstrapConfigs(String componentId) {
        writeBootstrapConfigs(componentId, ComponentTakingConfig.class);
    }

    protected void writeBootstrapConfigs() {
        writeBootstrapConfigs(ComponentTakingConfig.class.getName(), ComponentTakingConfig.class);
    }

    protected class ComponentEntry {
        private final String componentId;
        private final Class<?> classId;

        ComponentEntry(String componentId, Class<?> classId) {
            this.componentId = componentId;
            this.classId = classId;
        }

        String asConfig(int position) {
            return "<config>\n" + //
                    "components[" + position + "].id \"" + componentId + "\"\n" + //
                    "components[" + position + "].classId \"" + classId.getName() + "\"\n" + //
                    "components[" + position + "].configId \"" + dirConfigSource.configId() + "\"\n" + //
                    "</config>";
        }
    }
}
