// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import java.util.*;

import static com.yahoo.container.jdisc.JdiscBindingsConfig.Handlers;

/**
 * @author gjoranv
 * @since 5.1.8
 */
public class DiscBindingsConfigGenerator {

    public static Map<String, Handlers.Builder> generate(Collection<? extends Handler<?>> handlers) {
        Map<String, Handlers.Builder> handlerBuilders = new LinkedHashMap<>();

        for (Handler<?> handler : handlers) {
            handlerBuilders.putAll(generate(handler));
        }
        return handlerBuilders;
    }

    public static <T extends Handler<?>> Map<String, Handlers.Builder> generate(T handler) {
        if (handler.getServerBindings().isEmpty() && handler.getClientBindings().isEmpty())
            return Collections.emptyMap();

        return Collections.singletonMap(handler.model.getComponentId().stringValue(),
                            new Handlers.Builder()
                                    .serverBindings(handler.getServerBindings())
                                    .clientBindings(handler.getClientBindings()));
    }
}
