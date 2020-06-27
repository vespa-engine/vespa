// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.billing;

/**
 * A Plan decides two different things:
 *
 * - How to map from usage to a sum of money that is owed.
 * - Limits on how much resources can be used.
 *
 * @author ogronnesby
 */
public interface Plan {

    /** The ID of the plan as used in APIs and storage systems */
    String id();

    /** The calculator used to calculate a bill for usage */
    CostCalculator calculator();

    /** The quota limits associated with the plan */
    Object quota();

}
