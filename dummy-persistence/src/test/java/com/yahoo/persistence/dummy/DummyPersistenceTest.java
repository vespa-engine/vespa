// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.dummy;

import com.yahoo.document.DocumentTypeManager;
import com.yahoo.persistence.PersistenceRpcConfig;
import com.yahoo.persistence.rpc.PersistenceProviderHandler;
import com.yahoo.persistence.spi.PersistenceProvider;
import com.yahoo.persistence.spi.conformance.ConformanceTest;

public class DummyPersistenceTest extends ConformanceTest {

    class DummyPersistenceFactory implements PersistenceProviderFactory {

        @Override
        public PersistenceProvider createProvider(DocumentTypeManager manager) {
            return new DummyPersistenceProvider();
        }

        @Override
        public boolean supportsActiveState() {
            return true;
        }
    }

    public void testConstruct() {
        DummyPersistenceProviderHandler provider = new DummyPersistenceProviderHandler(
                new PersistenceProviderHandler(new PersistenceRpcConfig(new PersistenceRpcConfig.Builder())), null);
    }

    public void testConformance() throws Exception {
        doConformanceTest(new DummyPersistenceFactory());
    }
}
