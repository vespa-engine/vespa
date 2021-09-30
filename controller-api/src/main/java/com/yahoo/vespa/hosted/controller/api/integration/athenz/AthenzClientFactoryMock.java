// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athenz;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.client.zms.ZmsClient;
import com.yahoo.vespa.athenz.client.zts.ZtsClient;

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
    public AthenzService getControllerIdentity() {
        return new AthenzService("vespa.hosting");
    }

    @Override
    public ZmsClient createZmsClient() {
        return new ZmsClientMock(athenz, getControllerIdentity());
    }

    @Override
    public ZtsClient createZtsClient() {
        return new ZtsClientMock(athenz);
    }

}
