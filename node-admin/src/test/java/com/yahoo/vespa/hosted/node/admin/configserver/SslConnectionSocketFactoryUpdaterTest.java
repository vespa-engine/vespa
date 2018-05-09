// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver;

import com.yahoo.vespa.athenz.identity.SiaIdentityProvider;
import com.yahoo.vespa.athenz.tls.SslContextBuilder;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author bjorncs
 */
public class SslConnectionSocketFactoryUpdaterTest {

    @Test
    public void creates_default_ssl_connection_factory_when_no_sia_provided() {
        SslConnectionSocketFactoryUpdater updater =
                new SslConnectionSocketFactoryUpdater(null, (hostname, session) -> true);
        assertNotNull(updater.getCurrentSocketFactory());
    }

    @Test
    public void creates_ssl_connection_factory_when_sia_provided() {
        SiaIdentityProvider sia = mock(SiaIdentityProvider.class);
        when(sia.getIdentitySslContext()).thenReturn(new SslContextBuilder().build());
        SslConnectionSocketFactoryUpdater updater = new SslConnectionSocketFactoryUpdater(sia, (hostname, session) -> true);
        assertNotNull(updater.getCurrentSocketFactory());
    }
}