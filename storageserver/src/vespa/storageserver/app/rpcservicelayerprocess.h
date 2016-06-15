// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::RpcServiceLayerProcess
 *
 * \brief A process running a service layer with RPC persistence provider.
 */
#pragma once

#include <vespa/persistence/proxy/providerproxy.h>
#include <vespa/storageserver/app/servicelayerprocess.h>

namespace storage {

class RpcServiceLayerProcess : public ServiceLayerProcess {
    spi::ProviderProxy::UP _provider;

public:
    RpcServiceLayerProcess(const config::ConfigUri & configUri);
    ~RpcServiceLayerProcess() { shutdown(); }

    virtual void shutdown();
    virtual void setupProvider();
    virtual void updateConfig();
    virtual spi::PersistenceProvider& getProvider() { return *_provider; }
};

} // storage

