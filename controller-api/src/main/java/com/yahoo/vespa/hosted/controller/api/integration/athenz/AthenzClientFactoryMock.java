// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athenz;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.client.zms.ZmsClient;
import com.yahoo.vespa.athenz.client.zts.ZtsClient;

/**
 * @author bjorncs
 */
public class AthenzClientFactoryMock extends AbstractComponent implements AthenzClientFactory {

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
