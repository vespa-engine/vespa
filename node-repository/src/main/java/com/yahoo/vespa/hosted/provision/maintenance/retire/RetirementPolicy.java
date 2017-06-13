package com.yahoo.vespa.hosted.provision.maintenance.retire;

import com.yahoo.vespa.hosted.provision.Node;

/**
 * @author freva
 */
public interface RetirementPolicy {
    boolean shouldRetire(Node node);
}
