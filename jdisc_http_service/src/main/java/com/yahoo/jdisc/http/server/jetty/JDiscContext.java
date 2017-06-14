// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.application.BindingSet;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.jdisc.http.filter.RequestFilter;
import com.yahoo.jdisc.http.filter.ResponseFilter;
import com.yahoo.jdisc.service.CurrentContainer;

import java.util.concurrent.Executor;

public class JDiscContext {
    final BindingSet<RequestFilter> requestFilters;
    final BindingSet<ResponseFilter> responseFilters;
    final CurrentContainer container;
    final Executor janitor;
    final Metric metric;
    final ServerConfig serverConfig;

    public JDiscContext(BindingSet<RequestFilter> requestFilters,
                        BindingSet<ResponseFilter> responseFilters,
                        CurrentContainer container,
                        Executor janitor,
                        Metric metric,
                        ServerConfig serverConfig) {

        this.requestFilters = requestFilters;
        this.responseFilters = responseFilters;
        this.container = container;
        this.janitor = janitor;
        this.metric = metric;
        this.serverConfig = serverConfig;
    }

    public boolean developerMode() {
        return serverConfig.developerMode();
    }
}
