package com.yahoo.vespa.hosted.controller.api.integration;

public interface ApplicationIdSource {

    /** Returns a snapshot of all known tenants, applications and instances */
    ApplicationIdSnapshot applicationIdSnapshot();
}
