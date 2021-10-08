// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.billing;

/**
 * Calculates the quota.  This is used in the context of a {@link Plan}.
 *
 * @author ogronnesby
 */
public interface QuotaCalculator {
    /** Calculate the quota for a given environment */
    Quota calculate();
}
