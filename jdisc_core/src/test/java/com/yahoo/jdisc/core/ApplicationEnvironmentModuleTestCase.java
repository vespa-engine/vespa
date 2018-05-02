// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.google.inject.*;
import com.yahoo.jdisc.application.ContainerActivator;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.application.OsgiFramework;
import com.yahoo.jdisc.service.CurrentContainer;
import com.yahoo.jdisc.test.NonWorkingOsgiFramework;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadFactory;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class ApplicationEnvironmentModuleTestCase {

    @Test
    public void requireThatBindingsExist() {
        List<Class<?>> expected = new LinkedList<>();
        expected.add(ContainerActivator.class);
        expected.add(ContainerBuilder.class);
        expected.add(CurrentContainer.class);
        expected.add(OsgiFramework.class);
        expected.add(ThreadFactory.class);

        Injector injector = Guice.createInjector();
        for (Map.Entry<Key<?>, Binding<?>> entry : injector.getBindings().entrySet()) {
            expected.add(entry.getKey().getTypeLiteral().getRawType());
        }

        ApplicationLoader loader = new ApplicationLoader(new NonWorkingOsgiFramework(),
                                                         Collections.<Module>emptyList());
        injector = Guice.createInjector(new ApplicationEnvironmentModule(loader));
        for (Map.Entry<Key<?>, Binding<?>> entry : injector.getBindings().entrySet()) {
            assertNotNull(expected.remove(entry.getKey().getTypeLiteral().getRawType()));
        }
        assertTrue(expected.isEmpty());
    }

    @Test
    public void requireThatContainerBuilderCanBeInjected() {
        ApplicationLoader loader = new ApplicationLoader(new NonWorkingOsgiFramework(),
                                                         Collections.<Module>emptyList());
        assertNotNull(new ApplicationEnvironmentModule(loader).containerBuilder());
        assertNotNull(Guice.createInjector(new ApplicationEnvironmentModule(loader))
                           .getInstance(ContainerBuilder.class));
    }
}
