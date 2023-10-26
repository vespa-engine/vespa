// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing;

/**
 * Endpoint configurations supported for an application.
 *
 * @author mpolden
 */
public enum EndpointConfig {

    /** Only legacy endpoints will be published in DNS. Certificate will contain both legacy and generated names, and is never assigned from a pool */
    legacy,

    /** Legacy and generated endpoints will be published in DNS. Certificate will contain both legacy and generated names, and is never assigned from a pool */
    combined,

    /** Only generated endpoints will be published in DNS. Certificate will contain generated names only. Certificate is assigned from a pool */
    generated;

    /** Returns whether this config supports legacy endpoints */
    public boolean supportsLegacy() {
        return this == legacy || this == combined;
    }

    /** Returns whether this config supports generated endpoints */
    public boolean supportsGenerated() {
        return this == combined || this == generated;
    }

}
