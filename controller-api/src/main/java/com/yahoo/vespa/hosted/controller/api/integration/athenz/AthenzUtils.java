// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athenz;

import com.yahoo.vespa.hosted.controller.api.identifiers.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;

/**
 * @author bjorncs
 */
public class AthenzUtils {

    private AthenzUtils() {}

    public static final AthenzDomain USER_PRINCIPAL_DOMAIN = new AthenzDomain("user");
    public static final AthenzDomain SCREWDRIVER_DOMAIN = new AthenzDomain("cd.screwdriver.project");
    public static final AthenzService ZMS_ATHENZ_SERVICE = new AthenzService("sys.auth", "zms");

    public static AthenzIdentity createAthenzIdentity(AthenzDomain domain, String identityName) {
        if (domain.equals(USER_PRINCIPAL_DOMAIN)) {
            return AthenzUser.fromUserId(new UserId(identityName));
        } else {
            return new AthenzService(domain, identityName);
        }
    }

}
