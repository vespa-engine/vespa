// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import ai.vespa.validation.PatternedStringWrapper;

import java.util.regex.Pattern;

/**
 * The name of a cloud provider availability zone within a region.
 * An AZ name must be all lowercase, start with a letter, and contain letters and digits, separated by dashes.
 *
 * @author bratseth
 */
public class AzName extends PatternedStringWrapper<AzName> {

    private static final Pattern pattern = Pattern.compile("[a-z]([a-z0-9-]*[a-z0-9])*");
    private static final AzName defaultName = from("default");

    private AzName(String az) {
        super(az, pattern, "availability zone name");
    }

    /** The special name which means to use the zone's default as. */
    public static AzName defaultName() {
        return defaultName;
    }

    public static AzName from(String az) {
        return new AzName(az);
    }

}
