// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zms.bindings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author olaa
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StatisticsEntity {

    private int subdomains;
    private int roles;
    private int policies;
    private int services;
    private int groups;

    public StatisticsEntity(@JsonProperty("subdomain") int subdomains,
                            @JsonProperty("role") int roles,
                            @JsonProperty("policy") int policies,
                            @JsonProperty("service") int services,
                            @JsonProperty("group") int groups) {
        this.subdomains = subdomains;
        this.roles = roles;
        this.policies = policies;
        this.services = services;
        this.groups = groups;
    }

    public int getSubdomains() {
        return subdomains;
    }

    public int getRoles() {
        return roles;
    }

    public int getPolicies() {
        return policies;
    }

    public int getServices() {
        return services;
    }

    public int getGroups() {
        return groups;
    }

}