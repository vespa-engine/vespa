// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import ai.vespa.validation.PatternedStringWrapper;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Represents a cloud provider used in a hosted Vespa system.
 *
 * @author mpolden
 */
public class CloudName extends PatternedStringWrapper<CloudName> {

    private static final Pattern pattern = Pattern.compile("[a-z]([a-z0-9-]*[a-z0-9])*");
    private static final CloudName defaultCloud = from("default");

    private CloudName(String cloud) {
        super(cloud, pattern, "cloud name");
    }

    public boolean isDefault() {
        return equals(defaultCloud);
    }

    public static CloudName defaultName() {
        return defaultCloud;
    }

    public static CloudName from(String cloud) {
        return new CloudName(cloud);
    }

}
