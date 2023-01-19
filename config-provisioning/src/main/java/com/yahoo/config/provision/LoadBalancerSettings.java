package com.yahoo.config.provision;

import java.util.List;

/**
 * Settings for a load balancer provisioned for an application container cluster.
 *
 * TODO: Remove once 8.113 is gone
 *
 * @author jonmv
 */
public record LoadBalancerSettings(List<String> allowedUrns) {

    public static final LoadBalancerSettings empty = new LoadBalancerSettings(List.of());

    public LoadBalancerSettings(List<String> allowedUrns) {
        this.allowedUrns = List.copyOf(allowedUrns);
    }

    public boolean isEmpty() { return allowedUrns.isEmpty(); }

}
