// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.dummy;

import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.persistence.rpc.PersistenceProviderHandler;

public class DummyPersistenceProviderHandler {
    DummyPersistenceProvider provider;

    public DummyPersistenceProviderHandler(PersistenceProviderHandler rpcHandler, DocumentmanagerConfig docManConfig) {
        provider = new DummyPersistenceProvider();
        rpcHandler.initialize(provider, new DocumentTypeManager(docManConfig));
    }
}
