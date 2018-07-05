// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.servlet.jersey.util;

import org.glassfish.jersey.server.ResourceConfig;

/**
 * @author Tony Vaagenes
 */
public class ResourceConfigUtil {
    /**
     * Solves ambiguous reference to overloaded definition, see
     * http://stackoverflow.com/questions/3313929/how-do-i-disambiguate-in-scala-between-methods-with-vararg-and-without
     */
    public static void registerComponent(ResourceConfig config, Object component) {
        config.register(component);
    }
}
