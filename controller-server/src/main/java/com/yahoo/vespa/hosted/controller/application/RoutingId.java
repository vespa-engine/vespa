// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.RotationName;

import java.util.Objects;

/**
 * Unique identifier for a global routing table entry (application x rotation name).
 *
 * @author mpolden
 */
public class RoutingId {

    private final ApplicationId application;
    private final RotationName rotation;

    public RoutingId(ApplicationId application, RotationName rotation) {
        this.application = Objects.requireNonNull(application, "application must be non-null");
        this.rotation = Objects.requireNonNull(rotation, "rotation must be non-null");
    }

    public ApplicationId application() {
        return application;
    }

    public RotationName rotation() {
        return rotation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoutingId that = (RoutingId) o;
        return application.equals(that.application) &&
               rotation.equals(that.rotation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(application, rotation);
    }

}
