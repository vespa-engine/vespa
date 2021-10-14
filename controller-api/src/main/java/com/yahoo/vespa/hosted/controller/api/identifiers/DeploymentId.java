// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.identifiers;

import com.yahoo.config.provision.zone.ZoneId;

import java.util.Objects;

/**
 * ApplicationId x ZoneId.
 * 
 * @author smorgrav
 * @author bratseth
 */
public class DeploymentId {

    private final com.yahoo.config.provision.ApplicationId applicationId;
    private final ZoneId zoneId;

    public DeploymentId(com.yahoo.config.provision.ApplicationId applicationId, ZoneId zoneId) {
        this.applicationId = applicationId;
        this.zoneId = zoneId;
    }

    public com.yahoo.config.provision.ApplicationId applicationId() {
        return applicationId;
    }

    public ZoneId zoneId() {
        return zoneId;
    }

    public String dottedString() {
        return unCapitalize(applicationId().tenant().value()) + "."
               + unCapitalize(applicationId().application().value()) + "."
               + unCapitalize(zoneId.environment().value()) + "."
               + unCapitalize(zoneId.region().value()) + "."
               + unCapitalize(applicationId.instance().value());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if ( ! (o instanceof DeploymentId)) return false;
        DeploymentId id = (DeploymentId) o;
        return Objects.equals(applicationId, id.applicationId) &&
               Objects.equals(zoneId, id.zoneId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(applicationId, zoneId);
    }

    @Override
    public String toString() {
        return toUserFriendlyString();
    }

    public String toUserFriendlyString() {
        return applicationId + " in " + zoneId;
    }

    private static String unCapitalize(String str) {
        return str.toLowerCase().substring(0,1) + str.substring(1);
    }

}
