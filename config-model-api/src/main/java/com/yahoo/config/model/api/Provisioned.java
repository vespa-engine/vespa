// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A recording of the capacity requests issued during a model build.
 * Requests are only recorded here if provision requests are issued to the node repo.
 *
 * @author bratseth
 */
public class Provisioned {

    private final Map<ClusterSpec.Id, Capacity> provisioned = new HashMap<>();

    public void add(ClusterSpec.Id id, Capacity capacity) {
        provisioned.put(id, capacity);
    }

    /** Returns an unmodifiable map of all the provision requests recorded during build of the model this belongs to */
    public Map<ClusterSpec.Id, Capacity> all() { return Collections.unmodifiableMap(provisioned); }

}
