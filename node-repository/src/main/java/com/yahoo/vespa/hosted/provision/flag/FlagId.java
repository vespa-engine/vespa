// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.flag;

import java.util.Arrays;

/**
 * Features of this node repository that can be toggled.
 *
 * @author mpolden
 */
public enum FlagId {

    /** Indicates whether a exclusive load balancer should be provisioned */
    exclusiveLoadBalancer("exclusive-load-balancer"),

    /** Temporary. Indicates whether to use the new cache generation counting, or the old one (with a known bug) */
    newCacheCounting("new-cache-counting");

    private final String serializedValue;

    FlagId(String serializedValue) {
        this.serializedValue = serializedValue;
    }

    public String serializedValue() {
        return serializedValue;
    }

    public static FlagId fromSerializedForm(String value) {
        return Arrays.stream(FlagId.values())
                     .filter(f -> f.serializedValue().equals(value))
                     .findFirst()
                     .orElseThrow(() -> new IllegalArgumentException("Could not find flag ID by serialized value '" +
                                                                     value + "'"));
    }

}
