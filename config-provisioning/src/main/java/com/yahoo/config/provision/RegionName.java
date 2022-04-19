// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import ai.vespa.validation.PatternedStringWrapper;

import java.util.regex.Pattern;

/**
 * A region in a hosted Vespa system.
 * A region name must be all lowercase, start with a letter, and contain letters and digits, separated by dashes.
 *
 * @author jonmv
 */
public class RegionName extends PatternedStringWrapper<RegionName> {

    private static final Pattern pattern = Pattern.compile("[a-z]([a-z0-9-]*[a-z0-9])*");
    private static final RegionName defaultName = from("default");

    private RegionName(String region) {
        super(region, pattern, "region name");
    }

    public static RegionName from(String region) {
        return new RegionName(region);
    }

    public static RegionName defaultName() {
        return defaultName;
    }

    public boolean isDefault() {
        return equals(defaultName());
    }

}
