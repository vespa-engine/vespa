// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athens;

import com.yahoo.vespa.hosted.controller.api.identifiers.AthensDomain;
import com.yahoo.vespa.hosted.controller.api.identifiers.ScrewdriverId;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;

/**
 * Interface for integrating controller with Athens.
 *
 * @author mpolden
 */
public interface Athens {

    String principalTokenHeader();
    AthensPrincipal principalFrom(ScrewdriverId screwdriverId);
    AthensPrincipal principalFrom(UserId userId);
    NTokenValidator validator();
    NToken nTokenFrom(String rawToken);
    ZmsClientFactory zmsClientFactory();
    AthensDomain screwdriverDomain();

}
