// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.jrt.slobrok.api.Mirror;

import java.util.List;

public interface SlobrokApi extends ServiceStatusProvider {
    /**
     * Get all Slobrok entries that has a name matching pattern as described in
     * Mirror::lookup.
     */
    List<Mirror.Entry> lookup(ApplicationId application, String pattern);
}
