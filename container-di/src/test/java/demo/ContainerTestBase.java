// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package demo;

import com.google.inject.Guice;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.config.FileReference;
import com.yahoo.container.di.CloudSubscriberFactory;
import com.yahoo.container.di.Container;
import com.yahoo.container.di.ContainerTest;
import com.yahoo.container.di.Osgi;
import com.yahoo.container.di.componentgraph.core.ComponentGraph;
import org.junit.Before;
import org.osgi.framework.Bundle;
import scala.collection.immutable.Set;

import java.util.Collection;

/**
 * @author tonytv
 * @author gjoranv
 */
public class ContainerTestBase extends ContainerTest {
    private ComponentGraph componentGraph;

    @Before
    public void createGraph() {
        componentGraph = new ComponentGraph(0);
    }

    public void complete() {
        try {
            Container container = new Container(
                    new CloudSubscriberFactory(dirConfigSource().configSource()),
                    dirConfigSource().configId(),
                    new ContainerTest.TestDeconstructor(),
                    new Osgi() {
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
                        public BundleClasses getBundleClasses(ComponentSpecification bundle,
                                                              Set<String> packagesToScan) {
                            throw new UnsupportedOperationException("getBundleClasses not supported");
                        }

                        @Override
                        public void useBundles(Collection<FileReference> bundles) {}

                        @Override
                        public Bundle getBundle(ComponentSpecification spec) {
                            throw new UnsupportedOperationException("getBundle not supported.");
                        }
                    });
            componentGraph = container.getNewConfigGraph(componentGraph, Guice.createInjector(), false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T getInstance(Class<T> componentClass) {
        return componentGraph.getInstance(componentClass);
    }
}
