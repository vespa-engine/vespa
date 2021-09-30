// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.servlet;

import javax.servlet.Servlet;

import com.yahoo.container.di.componentgraph.Provider;
import org.eclipse.jetty.servlet.ServletHolder;

/**
 * @author stiankri
 */
public class ServletProvider implements Provider<ServletHolder> {

    private final ServletHolder servletHolder;

    public ServletProvider(Servlet servlet, ServletConfigConfig servletConfigConfig) {
        servletHolder = new ServletHolder(servlet);
        servletConfigConfig.map().forEach( (key, value) -> servletHolder.setInitParameter(key, value));
    }

    @Override
    public ServletHolder get() {
        return servletHolder;
    }

    @Override
    public void deconstruct() { }
}
