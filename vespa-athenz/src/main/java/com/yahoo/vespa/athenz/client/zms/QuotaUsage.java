// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zms;

import com.yahoo.vespa.athenz.client.zms.bindings.StatisticsEntity;

/**
 * @author olaa
 */
public class QuotaUsage {

    private double subdomainUsage;
    private double roleUsage;
    private double policyUsage;
    private double serviceUsage;
    private double groupUsage;


    public QuotaUsage(double subdomainUsage, double roleUsage, double policyUsage, double serviceUsage, double groupUsage) {
        this.subdomainUsage = subdomainUsage;
        this.roleUsage = roleUsage;
        this.policyUsage = policyUsage;
        this.serviceUsage = serviceUsage;
        this.groupUsage = groupUsage;
    }

    public static QuotaUsage calculateUsage(StatisticsEntity used, StatisticsEntity quota) {
        return new QuotaUsage(
                (double) used.getSubdomains() / quota.getSubdomains(),
                (double) used.getRoles() / quota.getRoles(),
                (double) used.getPolicies() / quota.getPolicies(),
                (double) used.getServices() / quota.getServices(),
                (double) used.getGroups() / quota.getGroups()
        );
    }

    public double getSubdomainUsage() {
        return subdomainUsage;
    }

    public double getRoleUsage() {
        return roleUsage;
    }

    public double getPolicyUsage() {
        return policyUsage;
    }

    public double getServiceUsage() {
        return serviceUsage;
    }

    public double getGroupUsage() {
        return groupUsage;
    }
}