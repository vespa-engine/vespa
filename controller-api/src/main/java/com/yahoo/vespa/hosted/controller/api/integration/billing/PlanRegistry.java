package com.yahoo.vespa.hosted.controller.api.integration.billing;

import java.util.Optional;

/**
 * Registry of all current plans we have support for
 *
 * @author ogronnesby
 */
public interface PlanRegistry {

    /** Get the default plan */
    Plan defaultPlan();

    /** Get a plan given a plan ID */
    Optional<Plan> plan(PlanId planId);

}
