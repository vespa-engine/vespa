// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.mock;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.vespa.athenz.api.NToken;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ZmsClient;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactory;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ZtsClient;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author bjorncs
 */
public class AthenzClientFactoryMock extends AbstractComponent implements AthenzClientFactory {

    private static final Logger log = Logger.getLogger(AthenzClientFactoryMock.class.getName());

    private final AthenzDbMock athenz;

    @Inject
    public AthenzClientFactoryMock() {
        this(new AthenzDbMock());
    }

    public AthenzClientFactoryMock(AthenzDbMock athenz) {
        this.athenz = athenz;
    }

    public AthenzDbMock getSetup() {
        return athenz;
    }

    @Override
    public ZmsClient createZmsClientWithServicePrincipal() {
        log("createZmsClientWithServicePrincipal()");
        return new ZmsClientMock(athenz);
    }

    @Override
    public ZtsClient createZtsClientWithServicePrincipal() {
        log("createZtsClientWithServicePrincipal()");
        return new ZtsClientMock(athenz);
    }

    @Override
    public ZmsClient createZmsClientWithAuthorizedServiceToken(NToken authorizedServiceToken) {
        log("createZmsClientWithAuthorizedServiceToken(authorizedServiceToken='%s')", authorizedServiceToken);
        return new ZmsClientMock(athenz);
    }

    private static void log(String format, Object... args) {
        log.log(Level.INFO, String.format(format, args));
    }

}
