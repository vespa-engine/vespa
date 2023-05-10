// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import com.yahoo.vespa.applicationmodel.InfrastructureApplication;
import com.yahoo.vespa.hosted.provision.maintenance.LoadBalancerExpirer;
import com.yahoo.vespa.service.duper.ConfigServerApplication;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Represents a load balancer for an application's cluster. This is immutable.
 *
 * @author mpolden
 */
public class LoadBalancer {

    private final LoadBalancerId id;
    private final Optional<LoadBalancerInstance> instance;
    private final State state;
    private final Instant changedAt;

    public LoadBalancer(LoadBalancerId id, Optional<LoadBalancerInstance> instance, State state, Instant changedAt) {
        this.id = Objects.requireNonNull(id, "id must be non-null");
        this.instance = Objects.requireNonNull(instance, "instance must be non-null");
        this.state = Objects.requireNonNull(state, "state must be non-null");
        this.changedAt = Objects.requireNonNull(changedAt, "changedAt must be non-null");
        if (state == State.active && instance.isEmpty()) {
            throw new IllegalArgumentException("Load balancer instance is required in state " + state);
        }
        if (id.application().equals(InfrastructureApplication.CONFIG_SERVER.id()) &&
            Set.of(State.inactive, State.removable).contains(state)) {
            throw new IllegalArgumentException("The config server load balancer is managed by Terraform - therefore the state cannot be '" + state + "'");
        }
    }

    /** An identifier for this load balancer. The ID is unique inside the zone */
    public LoadBalancerId id() {
        return id;
    }

    /** The instance associated with this */
    public Optional<LoadBalancerInstance> instance() {
        return instance;
    }

    /** The current state of this */
    public State state() {
        return state;
    }

    /** Returns when this was last changed */
    public Instant changedAt() {
        return changedAt;
    }

    /** Returns a copy of this with state set to given state */
    public LoadBalancer with(State state, Instant changedAt) {
        if (changedAt.isBefore(this.changedAt)) {
            throw new IllegalArgumentException("Invalid changeAt: '" + changedAt + "' is before existing value '" +
                                               this.changedAt + "'");
        }
        if (this.state != State.reserved && state == State.reserved) {
            throw new IllegalArgumentException("Invalid state transition: " + this.state + " -> " + state);
        }
        return new LoadBalancer(id, instance, state, changedAt);
    }

    /** Returns a copy of this with instance set to given instance */
    public LoadBalancer with(Optional<LoadBalancerInstance> instance) {
        return new LoadBalancer(id, instance, state, changedAt);
    }

    public enum State {

        /** The load balancer has been provisioned and reserved for an application */
        reserved,

        /**
         * The load balancer has been deactivated and is ready to be removed. Inactive load balancers are eventually
         * removed by {@link LoadBalancerExpirer}. Inactive load balancers may be reactivated if a deleted cluster is
         * redeployed.
         */
        inactive,

        /** The load balancer is in active use by an application */
        active,

        /** The load balancer should be removed immediately by {@link LoadBalancerExpirer} */
        removable,

    }

}
