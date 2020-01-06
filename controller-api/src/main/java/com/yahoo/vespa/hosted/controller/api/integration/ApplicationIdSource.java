// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration;

public interface ApplicationIdSource {

    /** Returns a snapshot of all known tenants, applications and instances */
    ApplicationIdSnapshot applicationIdSnapshot();
}
