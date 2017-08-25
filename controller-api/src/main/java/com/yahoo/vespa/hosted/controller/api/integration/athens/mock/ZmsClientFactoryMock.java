// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athens.mock;

import com.yahoo.component.AbstractComponent;
import com.yahoo.vespa.hosted.controller.api.integration.athens.NToken;
import com.yahoo.vespa.hosted.controller.api.integration.athens.ZmsClient;
import com.yahoo.vespa.hosted.controller.api.integration.athens.ZmsClientFactory;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author bjorncs
 */
public class ZmsClientFactoryMock extends AbstractComponent implements ZmsClientFactory {

    private static final Logger log = Logger.getLogger(ZmsClientFactoryMock.class.getName());

    private final AthensDbMock athens;

    public ZmsClientFactoryMock() {
        this(new AthensDbMock());
    }

    ZmsClientFactoryMock(AthensDbMock athens) {
        this.athens = athens;
    }

    public AthensDbMock getSetup() {
        return athens;
    }

    @Override
    public ZmsClient createClientWithServicePrincipal() {
        log("createClientWithServicePrincipal()");
        return new ZmsClientMock(athens);
    }

    @Override
    public ZmsClient createClientWithAuthorizedServiceToken(NToken authorizedServiceToken) {
        log("createClientWithAuthorizedServiceToken(authorizedServiceToken='%s')", authorizedServiceToken);
        return new ZmsClientMock(athens);
    }

    @Override
    public ZmsClient createClientWithoutPrincipal() {
        log("createClientWithoutPrincipal()");
        return new ZmsClientMock(athens);
    }

    private static void log(String format, Object... args) {
        log.log(Level.INFO, String.format(format, args));
    }

}
