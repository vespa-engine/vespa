// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import org.osgi.framework.Bundle;
import org.osgi.framework.Version;

/**
 * A bundle's symbolic name and version.
 *
 * @author gjoranv
 */
public record BsnVersion(String symbolicName, Version version) {

    public BsnVersion(Bundle bundle) {
        this(bundle.getSymbolicName(), bundle.getVersion());
    }

    public String toReadableString() {
        return symbolicName + " version:" + version;
    }

}
