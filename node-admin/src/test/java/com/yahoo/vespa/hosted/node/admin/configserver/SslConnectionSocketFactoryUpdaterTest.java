// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver;

import com.yahoo.vespa.hosted.node.admin.component.ConfigServerInfo;
import com.yahoo.vespa.hosted.node.admin.configserver.certificate.ConfigServerKeyStoreRefresher;
import com.yahoo.vespa.hosted.node.admin.configserver.certificate.ConfigServerKeyStoreRefresherFactory;
import com.yahoo.vespa.hosted.node.admin.util.KeyStoreOptions;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author hakon
 */
public class SslConnectionSocketFactoryUpdaterTest {
    private final ConfigServerInfo configServerInfo = mock(ConfigServerInfo.class);
    private final String hostname = "host.oath.com";
    private final ConfigServerKeyStoreRefresherFactory refresherFactory =
            mock(ConfigServerKeyStoreRefresherFactory.class);
    private final ConfigServerKeyStoreRefresher refresher =
            mock(ConfigServerKeyStoreRefresher.class);
    private final SslConnectionSocketFactoryCreator socketFactoryCreator =
            mock(SslConnectionSocketFactoryCreator.class);
    private final SSLConnectionSocketFactory socketFactory = mock(SSLConnectionSocketFactory.class);

    @Before
    public void setUp() {
        KeyStoreOptions keyStoreOptions = mock(KeyStoreOptions.class);
        when(configServerInfo.getKeyStoreOptions()).thenReturn(Optional.of(keyStoreOptions));
        when(refresherFactory.create(any(), any(), any(), any())).thenReturn(refresher);
        when(socketFactoryCreator.createSocketFactory(any(), any()))
                .thenReturn(socketFactory);
    }

    @Test
    public void testSettingOfSocketFactory() {
        SslConnectionSocketFactoryUpdater updater = new SslConnectionSocketFactoryUpdater(
                configServerInfo,
                hostname,
                refresherFactory,
                socketFactoryCreator);

        assertTrue(socketFactory == updater.getCurrentSocketFactory());

        ConfigServerApi api = mock(ConfigServerApi.class);
        updater.registerConfigServerApi(api);
        verify(api, times(1)).setSSLConnectionSocketFactory(socketFactory);
    }
}