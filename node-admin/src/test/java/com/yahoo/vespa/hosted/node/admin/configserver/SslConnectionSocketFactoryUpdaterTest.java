// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver;

import com.yahoo.vespa.hosted.node.admin.component.ConfigServerInfo;
import com.yahoo.vespa.hosted.node.admin.configserver.certificate.ConfigServerKeyStoreRefresherFactory;
import com.yahoo.vespa.hosted.node.admin.util.KeyStoreOptions;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.junit.Test;

import java.util.Optional;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author hakon
 */
public class SslConnectionSocketFactoryUpdaterTest {
    private final ConfigServerInfo configServerInfo = mock(ConfigServerInfo.class);
    private final String hostname = "host.oath.com";
    private final ConfigServerKeyStoreRefresherFactory refresherFactory =
            mock(ConfigServerKeyStoreRefresherFactory.class);
    private final SslConnectionSocketFactoryCreator socketFactoryCreator =
            mock(SslConnectionSocketFactoryCreator.class);

    @Test
    public void testBasics() {
        SSLConnectionSocketFactory socketFactory = mock(SSLConnectionSocketFactory.class);
        when(socketFactoryCreator.createSocketFactory(any(), any()))
                .thenReturn(socketFactory);

        Optional<KeyStoreOptions> keyStoreOptions = Optional.empty();
        when(configServerInfo.getKeyStoreOptions()).thenReturn(keyStoreOptions);

        SslConnectionSocketFactoryUpdater updater = new SslConnectionSocketFactoryUpdater(
                configServerInfo,
                hostname,
                refresherFactory,
                socketFactoryCreator);
    }

}