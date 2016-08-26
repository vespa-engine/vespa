// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import java.util.IdentityHashMap;
import java.util.Map;

import com.google.inject.Inject;
import com.yahoo.container.QrSearchersConfig;

/**
 * API for programmatically removing the container from VIP rotation.
 *
 * @author steinar
 */
public class VipStatus {

    private final Map<Object, Boolean> clusters = new IdentityHashMap<>();

    public VipStatus() {
        this(null);
    }

    @Inject
    public VipStatus(QrSearchersConfig dispatchers) {
        // the config is not used for anything, it's just a dummy to create a
        // dependency link to which dispatchers are used
    }

    /**
     * Set a service or cluster into rotation.
     *
     * @param clusterIdentifier
     *            an object where the object identity will serve to identify the
     *            cluster or service
     */
    public void addToRotation(Object clusterIdentifier) {
        synchronized (clusters) {
            clusters.put(clusterIdentifier, Boolean.TRUE);
        }
    }

    /**
     * Set a service or cluster out of rotation.
     *
     * @param clusterIdentifier
     *            an object where the object identity will serve to identify the
     *            cluster or service
     */
    public void removeFromRotation(Object clusterIdentifier) {
        synchronized (clusters) {
            clusters.put(clusterIdentifier, Boolean.FALSE);
        }
    }

    /**
     * Tell whether the container is connected to any active services at all.
     *
     * @return true if at least one service or cluster is up, or if no services
     *         are registered (yet)
     */
    public boolean isInRotation() {
        synchronized (clusters) {
            // if no stored state, try serving
            if (clusters.size() == 0) {
                return true;
            }
            for (Boolean inRotation : clusters.values()) {
                if (inRotation) {
                    return true;
                }
            }
        }
        return false;
    }

}
