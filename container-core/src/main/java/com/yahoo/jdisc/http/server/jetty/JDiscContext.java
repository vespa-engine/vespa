// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.jdisc.service.CurrentContainer;

record JDiscContext(FilterResolver filterResolver,
                    CurrentContainer container,
                    Janitor janitor,
                    Metric metric,
                    boolean developerMode,
                    boolean removeRawPostBodyForWwwUrlEncodedPost) {

    public static JDiscContext of(FilterBindings filterBindings, CurrentContainer container,
                                  Janitor janitor, Metric metric, ServerConfig config) {
        return new JDiscContext(new FilterResolver(filterBindings, metric), container, janitor,
                                metric, config.developerMode(), config.removeRawPostBodyForWwwUrlEncodedPost());
    }

}
