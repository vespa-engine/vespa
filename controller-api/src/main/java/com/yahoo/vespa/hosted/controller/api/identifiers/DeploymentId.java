// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.identifiers;

import com.yahoo.config.provision.Zone;

/**
 * Application + zone. 
 * 
 * @author smorgrav
 * @author bratseth
 */
public class DeploymentId {

    private final com.yahoo.config.provision.ApplicationId application;
    private final Zone zone;

    public DeploymentId(com.yahoo.config.provision.ApplicationId application, Zone zone) {
        this.application = application;
        this.zone = zone;
    }

    public com.yahoo.config.provision.ApplicationId applicationId() {
        return application;
    }
    public Zone zone() { return zone; }


    public String dottedString() {
        return unCapitalize(applicationId().tenant().value()) + "."
             + unCapitalize(applicationId().application().value()) + "."
             + unCapitalize(zone.environment().value()) + "."
             + unCapitalize(zone.region().value()) + "."
             + unCapitalize(application.instance().value());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DeploymentId other = (DeploymentId) o;
        if ( ! this.application.equals(other.application)) return false;
        // TODO: Simplify when Zone implements equals
        if ( ! this.zone.environment().equals(other.zone.environment())) return false;
        if ( ! this.zone.region().equals(other.zone.region())) return false;
        return true;
    }

    @Override
    public int hashCode() {
        // TODO: Simplify when Zone implements hashCode
        return application.hashCode() + 
               7 * zone.environment().hashCode() +
               31 * zone.region().hashCode();
    }

    @Override
    public String toString() {
        return toUserFriendlyString();
    }

    public String toUserFriendlyString() {
        return application + " in " + zone;
    }

    private static String unCapitalize(String str) {
        return str.toLowerCase().substring(0,1) + str.substring(1);
    }
}
