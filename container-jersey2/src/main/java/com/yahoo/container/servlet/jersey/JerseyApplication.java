// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.servlet.jersey;

import javax.ws.rs.core.Application;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class JerseyApplication extends Application {
    private Set<Class<?>> classes;

    public JerseyApplication(Collection<Class<?>> resourcesAndProviderClasses) {
        this.classes = new HashSet<>(resourcesAndProviderClasses);
    }

    @Override
    public Set<Class<?>> getClasses() {
        return classes;
    }
}
