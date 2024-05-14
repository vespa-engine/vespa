// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::DummyServiceLayerProcess
 *
 * \brief A process running a service layer with dummy persistence provider.
 */
#pragma once

#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <vespa/storageserver/app/servicelayerprocess.h>

namespace storage {

class DummyServiceLayerProcess : public ServiceLayerProcess {
    std::unique_ptr<spi::PersistenceProvider> _provider;

public:
    explicit DummyServiceLayerProcess(const config::ConfigUri & configUri);
    ~DummyServiceLayerProcess() override {
        DummyServiceLayerProcess::shutdown();
    }

    void shutdown() override;
    void setupProvider() override;
    spi::PersistenceProvider& getProvider() override { return *_provider; }
};

} // storage

