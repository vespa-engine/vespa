// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.guiceModules;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.yahoo.component.provider.ComponentRegistry;

import org.eclipse.jetty.servlet.ServletHolder;

/**
 * @author Tony Vaagenes
 */
public class ServletModule implements Module {
    @Override
    public void configure(Binder binder) {
    }

    @Provides
    public ComponentRegistry<ServletHolder> servletHolderComponentRegistry() {
        return new ComponentRegistry<>();
    }

}
