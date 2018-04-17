// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.certificate;

import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerApi;
import com.yahoo.vespa.hosted.node.admin.util.KeyStoreOptions;

/**
 * @author hakon
 */
@FunctionalInterface
public interface ConfigServerKeyStoreRefresherFactory {
    ConfigServerKeyStoreRefresher create(
            KeyStoreOptions keyStoreOptions,
            Runnable keyStoreUpdatedCallback,
            ConfigServerApi configServerApi,
            String hostname);
}
