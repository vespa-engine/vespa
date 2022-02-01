// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDatabaseClient;

import java.util.function.Predicate;

/**
 * The load balancers of this node repo.
 *
 * @author bratseth
 * @author mpolden
 */
public class LoadBalancers {

    private final CuratorDatabaseClient db;

    public LoadBalancers(CuratorDatabaseClient db) {
        this.db = db;
    }

    /** Returns a filterable list of all load balancers in this repository */
    public LoadBalancerList list() {
        return list((ignored) -> true);
    }

    /** Returns a filterable list of load balancers belonging to given application */
    public LoadBalancerList list(ApplicationId application) {
        return list((id) -> id.application().equals(application));
    }

    private LoadBalancerList list(Predicate<LoadBalancerId> predicate) {
        return LoadBalancerList.copyOf(db.readLoadBalancers(predicate).values());
    }

}
