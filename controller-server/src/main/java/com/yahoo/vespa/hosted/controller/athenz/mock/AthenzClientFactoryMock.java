// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.mock;

import com.yahoo.component.AbstractComponent;
import com.yahoo.vespa.hosted.controller.athenz.NToken;
import com.yahoo.vespa.hosted.controller.athenz.ZmsClient;
import com.yahoo.vespa.hosted.controller.athenz.AthenzClientFactory;
import com.yahoo.vespa.hosted.controller.athenz.ZtsClient;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author bjorncs
 */
public class AthenzClientFactoryMock extends AbstractComponent implements AthenzClientFactory {

    private static final Logger log = Logger.getLogger(AthenzClientFactoryMock.class.getName());

    private final AthensDbMock athens;

    public AthenzClientFactoryMock() {
        this(new AthensDbMock());
    }

    public AthenzClientFactoryMock(AthensDbMock athens) {
        this.athens = athens;
    }

    public AthensDbMock getSetup() {
        return athens;
    }

    @Override
    public ZmsClient createZmsClientWithServicePrincipal() {
        log("createZmsClientWithServicePrincipal()");
        return new ZmsClientMock(athens);
    }

    @Override
    public ZtsClient createZtsClientWithServicePrincipal() {
        log("createZtsClientWithServicePrincipal()");
        return new ZtsClientMock(athens);
    }

    @Override
    public ZmsClient createZmsClientWithAuthorizedServiceToken(NToken authorizedServiceToken) {
        log("createZmsClientWithAuthorizedServiceToken(authorizedServiceToken='%s')", authorizedServiceToken);
        return new ZmsClientMock(athens);
    }

    private static void log(String format, Object... args) {
        log.log(Level.INFO, String.format(format, args));
    }

}
