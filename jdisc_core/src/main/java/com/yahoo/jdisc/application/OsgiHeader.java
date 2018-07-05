// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import org.osgi.framework.Bundle;

import java.util.List;

/**
 * This interface acts as a namespace for the supported OSGi bundle headers.
 *
 * @author Simon Thoresen Hult
 */
public abstract class OsgiHeader {

    public static final String APPLICATION = "X-JDisc-Application";
    public static final String PREINSTALL_BUNDLE = "X-JDisc-Preinstall-Bundle";
    public static final String PRIVILEGED_ACTIVATOR = "X-JDisc-Privileged-Activator";

    /**
     * Returns true if the named header is present in the manifest of the given bundle.
     *
     * @param bundle     The bundle whose manifest to check.
     * @param headerName The name of the header to check for.
     * @return True if header is present.
     */
    public static boolean isSet(Bundle bundle, String headerName) {
        return Boolean.valueOf(String.valueOf(bundle.getHeaders().get(headerName)));
    }

    /**
     * This method reads the named header from the manifest of the given bundle, and parses it as a comma-separated list
     * of values. If the header is not set, this method returns an empty list.
     *
     * @param bundle     The bundle whose manifest to parse the header from.
     * @param headerName The name of the header to parse.
     * @return A list of parsed header values, may be empty.
     */
    public static List<String> asList(Bundle bundle, String headerName) {
        return ContainerBuilder.safeStringSplit(bundle.getHeaders().get(headerName), ",");
    }
}
