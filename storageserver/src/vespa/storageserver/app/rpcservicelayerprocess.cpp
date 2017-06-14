// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpcservicelayerprocess.h"
#include <vespa/storage/config/config-stor-server.h>
#include <vespa/config/helper/configgetter.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".process.servicelayer");

namespace storage {

// RpcServiceLayerProcess implementation

RpcServiceLayerProcess::RpcServiceLayerProcess(const config::ConfigUri & configUri)
    : ServiceLayerProcess(configUri)
{
}

void
RpcServiceLayerProcess::shutdown()
{
    ServiceLayerProcess::shutdown();
    _provider.reset(0);
}

void
RpcServiceLayerProcess::setupProvider()
{
    std::unique_ptr<vespa::config::content::core::StorServerConfig> serverConfig =
        config::ConfigGetter<vespa::config::content::core::StorServerConfig>::getConfig(_configUri.getConfigId(), _configUri.getContext());

    _provider.reset(new spi::ProviderProxy(
            serverConfig->persistenceProvider.rpc.connectspec, *getTypeRepo()));
}

void
RpcServiceLayerProcess::updateConfig()
{
    ServiceLayerProcess::updateConfig();
    LOG(info, "Config updated. Sending new config to RPC proxy provider");
    _provider->setRepo(*getTypeRepo());
}

} // storage
