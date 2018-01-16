// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athenz;

import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.hosted.controller.api.identifiers.ScrewdriverId;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;

/**
 * @author bjorncs
 */
public class HostedAthenzIdentities {

    public static final AthenzDomain SCREWDRIVER_DOMAIN = new AthenzDomain("cd.screwdriver.project");

    private HostedAthenzIdentities() {}

    public static AthenzUser from(UserId userId) {
        return AthenzUser.fromUserId(userId.id());
    }

    public static AthenzService from(ScrewdriverId screwdriverId) {
        return new AthenzService(SCREWDRIVER_DOMAIN, "sd" + screwdriverId.id());
    }

}
