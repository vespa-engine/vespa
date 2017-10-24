// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz;

import com.yahoo.vespa.hosted.controller.api.identifiers.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.identifiers.ScrewdriverId;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;

/**
 * @author bjorncs
 */
public class AthenzUtils {

    private AthenzUtils() {}

    // TODO Change to "user" as primary user principal domain. Also support "yby" for a limited time as per recent Athenz changes
    public static final AthenzDomain USER_PRINCIPAL_DOMAIN = new AthenzDomain("yby");
    public static final AthenzDomain SCREWDRIVER_DOMAIN = new AthenzDomain("cd.screwdriver.project");
    public static final AthenzService ZMS_ATHENZ_SERVICE = new AthenzService("sys.auth", "zms");

    public static AthenzPrincipal createPrincipal(UserId userId) {
        return new AthenzPrincipal(USER_PRINCIPAL_DOMAIN, userId);
    }

    public static AthenzPrincipal createPrincipal(ScrewdriverId screwdriverId) {
        return new AthenzPrincipal(SCREWDRIVER_DOMAIN, new UserId("sd" + screwdriverId.id()));
    }


}
