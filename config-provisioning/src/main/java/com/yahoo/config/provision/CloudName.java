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
    public static final CloudName AWS = from("aws");
    public static final CloudName GCP = from("gcp");
    public static final CloudName DEFAULT = from("default");
    public static final CloudName YAHOO = from("yahoo");

    private CloudName(String cloud) {
        super(cloud, pattern, "cloud name");
    }

    public static CloudName from(String cloud) {
        return new CloudName(cloud);
    }

}
