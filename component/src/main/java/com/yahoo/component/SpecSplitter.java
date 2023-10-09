// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component;

/**
 * Splits and component id or component specification string into their constituent parts.
 * 
 * @author Tony Vaagenes
 */
class SpecSplitter {

    final String name;
    final String version;
    final ComponentId namespace;

    SpecSplitter(String spec) {
        int indexOfAlpha = spec.indexOf('@');
        String nameAndVersion = spec;
        if (indexOfAlpha != -1) {
            if (indexOfAlpha == spec.length() - 1) {
                throw new RuntimeException("Expected characters after '@'");
            }
            nameAndVersion = spec.substring(0, indexOfAlpha);
            namespace = ComponentId.fromString(spec.substring(indexOfAlpha + 1));
        } else {
            namespace = null;
        }

        int indexOfColon = nameAndVersion.indexOf(':');
        if (indexOfColon != -1) {
            if (indexOfColon == nameAndVersion.length() - 1) {
                throw new RuntimeException("Expected characters after ':'");
            }
            name = nameAndVersion.substring(0, indexOfColon);
            version = nameAndVersion.substring(indexOfColon+1);
        } else {
            name = nameAndVersion;
            version = null;
        }
    }

}
