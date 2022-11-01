// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di;

import com.google.inject.Guice;
import com.yahoo.container.core.config.BundleTestUtil;
import com.yahoo.container.core.config.TestOsgi;
import com.yahoo.container.di.ContainerTest.ComponentTakingConfig;
import com.yahoo.container.di.componentgraph.core.ComponentGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.osgi.framework.Bundle;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 * @author ollivir
 */
public class ContainerTestBase {

    private ComponentGraph componentGraph;
    protected DirConfigSource dirConfigSource = null;
    protected TestOsgi osgi;

    @TempDir
    File tmpDir;

    @BeforeEach
    public void setup() {
        dirConfigSource = new DirConfigSource(tmpDir);
        componentGraph = new ComponentGraph(0);
        osgi = new TestOsgi(BundleTestUtil.testBundles());
    }

    protected Container newContainer(DirConfigSource dirConfigSource,
                                            ComponentDeconstructor deconstructor) {
        return new Container(new CloudSubscriberFactory(dirConfigSource.configSource()),
                             dirConfigSource.configId(),
                             deconstructor,
                             osgi);
    }

    protected Container newContainer(DirConfigSource dirConfigSource) {
        return newContainer(dirConfigSource, new TestDeconstructor(osgi));
    }

    ComponentGraph getNewComponentGraph(Container container, ComponentGraph oldGraph) {
        Container.ComponentGraphResult result = container.waitForNextGraphGeneration(oldGraph, Guice.createInjector(), true);
        result.oldComponentsCleanupTask().run();
        return result.newGraph();
    }

    ComponentGraph getNewComponentGraph(Container container) {
        return container.waitForNextGraphGeneration(new ComponentGraph(), Guice.createInjector(), true).newGraph();
    }


    public <T> T getInstance(Class<T> componentClass) {
        return componentGraph.getInstance(componentClass);
    }

    protected void writeBootstrapConfigsWithBundles(List<String> applicationBundles, List<ComponentEntry> components) {
        writeBootstrapConfigs(components.toArray(new ComponentEntry[0]));
        StringBuilder bundles = new StringBuilder();
        for (int i = 0; i < applicationBundles.size(); i++) {
            bundles.append("bundles[" + i + "] \"" + applicationBundles.get(i) + "\"\n");
        }
        dirConfigSource.writeConfig("application-bundles", String.format("bundles[%s]\n%s", applicationBundles.size(), bundles));
    }

    protected void writeBootstrapConfigs(ComponentEntry... componentEntries) {
        dirConfigSource.writeConfig("platform-bundles", "");
        dirConfigSource.writeConfig("application-bundles", "");
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
            return  "components[" + position + "].id \"" + componentId + "\"\n" +
                    "components[" + position + "].classId \"" + classId.getName() + "\"\n" +
                    "components[" + position + "].configId \"" + dirConfigSource.configId() + "\"\n" ;
        }
    }


    public static class TestDeconstructor implements ComponentDeconstructor {

        final TestOsgi osgi;

        public TestDeconstructor(TestOsgi osgi) {
            this.osgi = osgi;
        }

        @Override
        public void deconstruct(long generation, List<Object> components, Collection<Bundle> bundles) {
            components.forEach(component -> {
                if (component instanceof ContainerTest.DestructableComponent vespaComponent) {
                    vespaComponent.deconstruct();
                }
            });
            bundles.forEach(osgi::removeBundle);
        }
    }

}
