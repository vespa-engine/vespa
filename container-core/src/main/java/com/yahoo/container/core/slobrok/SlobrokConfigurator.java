// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.core.slobrok;

import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.cloud.config.SlobroksConfig.Slobrok;
import com.yahoo.container.Container;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Configures which slobrok nodes the container should register with.
 * @author Tony Vaagenes
 */
public class SlobrokConfigurator {
    public SlobrokConfigurator(SlobroksConfig config) {
        Container.get().getRpcAdaptor().registerInSlobrok(
                connectionSpecs(config.slobrok()));
    }

    private static List<String> connectionSpecs(List<Slobrok> slobroks) {
        return slobroks.stream().
                map(Slobrok::connectionspec).
                collect(Collectors.toList());
    }
}
