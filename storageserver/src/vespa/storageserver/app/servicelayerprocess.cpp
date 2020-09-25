// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "servicelayerprocess.h"
#include <vespa/config/helper/configgetter.hpp>
#include <vespa/storage/common/i_storage_chain_builder.h>
#include <vespa/storage/config/config-stor-server.h>
#include <vespa/storage/storageserver/servicelayernode.h>
#include <vespa/searchvisitor/searchvisitor.h>

#include <vespa/log/log.h>
LOG_SETUP(".storageserver.service_layer_process");

namespace storage {

namespace {

bool configured_to_use_btree_db(const config::ConfigUri& config_uri) {
    using vespa::config::content::core::StorServerConfig;
    auto server_config = config::ConfigGetter<StorServerConfig>::getConfig(
            config_uri.getConfigId(), config_uri.getContext());
    return server_config->useContentNodeBtreeBucketDb;
}

}

ServiceLayerProcess::ServiceLayerProcess(const config::ConfigUri& configUri)
    : Process(configUri),
      _externalVisitors(),
      _node(),
      _storage_chain_builder(),
      _context(std::make_unique<framework::defaultimplementation::RealClock>(),
               configured_to_use_btree_db(configUri))
{
}

ServiceLayerProcess::~ServiceLayerProcess() = default;

void
ServiceLayerProcess::shutdown()
{
    Process::shutdown();
    _node.reset();
}

void
ServiceLayerProcess::createNode()
{
    _externalVisitors["searchvisitor"] = std::make_shared<streaming::SearchVisitorFactory>(_configUri);
    setupProvider();
    _node = std::make_unique<ServiceLayerNode>(_configUri, _context, *this, getProvider(), _externalVisitors);
    if (_storage_chain_builder) {
        _node->set_storage_chain_builder(std::move(_storage_chain_builder));
    }
    _node->init();
}

StorageNode&
ServiceLayerProcess::getNode() {
    return *_node;
}

StorageNodeContext&
ServiceLayerProcess::getContext() {
    return _context;
}

std::string
ServiceLayerProcess::getComponentName() const {
    return "servicelayer";
}

void
ServiceLayerProcess::set_storage_chain_builder(std::unique_ptr<IStorageChainBuilder> builder)
{
    _storage_chain_builder = std::move(builder);
}

} // storage
