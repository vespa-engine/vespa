// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.billing;

/**
 * A Plan knows about the billing calculations and the default quota for any tenant associated with the Plan.
 * The Plan can also enforce transitions from one plan to another.
 *
 * @author ogronnesby
 */
public interface Plan {

    /** Unique ID for the plan */
    PlanId id();

    /** A string to be used for display purposes */
    String displayName();

    /** The cost calculator for this plan */
    CostCalculator calculator();

    /** The quota for this plan */
    QuotaCalculator quota();

    /** Is this a plan that is billed */
    boolean isBilled();
}
