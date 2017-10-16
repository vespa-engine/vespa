// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athens.mock;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.vespa.hosted.controller.api.identifiers.AthensDomain;
import com.yahoo.vespa.hosted.controller.api.identifiers.ScrewdriverId;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;
import com.yahoo.vespa.hosted.controller.api.integration.athens.Athens;
import com.yahoo.vespa.hosted.controller.api.integration.athens.AthensPrincipal;
import com.yahoo.vespa.hosted.controller.api.integration.athens.InvalidTokenException;
import com.yahoo.vespa.hosted.controller.api.integration.athens.NToken;
import com.yahoo.vespa.hosted.controller.api.integration.athens.NTokenValidator;
import com.yahoo.vespa.hosted.controller.api.integration.athens.ZmsClientFactory;

/**
 * @author mpolden
 */
public class AthensMock extends AbstractComponent implements Athens {

    private static final AthensDomain userDomain = new AthensDomain("domain1");
    private static final AthensDomain screwdriverDomain = new AthensDomain("screwdriver-domain");

    private final ZmsClientFactory zmsClientFactory;
    private final NTokenValidator nTokenValidator;

    public AthensMock(AthensDbMock athensDb, NTokenValidator nTokenValidator) {
        this.zmsClientFactory = new ZmsClientFactoryMock(athensDb);
        this.nTokenValidator = nTokenValidator;
    }

    public AthensMock(AthensDbMock athensDbMock) {
        this(athensDbMock, mockValidator);
    }

    @Inject
    public AthensMock() {
        this(new AthensDbMock(), mockValidator);
    }

    @Override
    public String principalTokenHeader() {
        return "X-Athens-Token";
    }

    @Override
    public AthensPrincipal principalFrom(ScrewdriverId screwdriverId) {
        return new AthensPrincipal(screwdriverDomain, new UserId("screwdriver-" + screwdriverId.id()));
    }

    @Override
    public AthensPrincipal principalFrom(UserId userId) {
        return new AthensPrincipal(userDomain, userId);
    }

    @Override
    public NTokenValidator validator() {
        return nTokenValidator;
    }

    @Override
    public NToken nTokenFrom(String rawToken) {
        return new NTokenMock(rawToken);
    }

    @Override
    public ZmsClientFactory zmsClientFactory() {
        return zmsClientFactory;
    }

    @Override
    public AthensDomain screwdriverDomain() {
        return screwdriverDomain;
    }

    private static final NTokenValidator mockValidator = new NTokenValidator() {
        @Override
        public void preloadPublicKeys() {
        }

        @Override
        public AthensPrincipal validate(NToken nToken) throws InvalidTokenException {
            return nToken.getPrincipal();
        }
    };

}
