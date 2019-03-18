package com.yahoo.vespa.hosted.controller.api.integration.organization;

/**
 * A marketplace where purchase tokens can be validated and redeemed for payments.
 *
 * @author jonmv
 */
public interface Marketplace {

    /** Validates and translates the token to billing information which can be used to request payment. */
    BillingInfo resolveCustomer(String registrationToken);

}
