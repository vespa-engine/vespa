// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "servicelayerprocess.h"
#include <vespa/config/helper/configgetter.hpp>
#include <vespa/storage/common/content_bucket_db_options.h>
#include <vespa/storage/common/i_storage_chain_builder.h>
#include <vespa/storage/config/config-stor-server.h>
#include <vespa/storage/storageserver/servicelayernode.h>
#include <vespa/storageframework/defaultimplementation/clock/realclock.h>
#include <vespa/searchvisitor/searchvisitor.h>

#include <vespa/log/log.h>
LOG_SETUP(".storageserver.service_layer_process");

namespace storage {

namespace {

ContentBucketDbOptions bucket_db_options_from_config(const config::ConfigUri& config_uri) {
    using vespa::config::content::core::StorServerConfig;
    auto server_config = config::ConfigGetter<StorServerConfig>::getConfig(
            config_uri.getConfigId(), config_uri.getContext());
    // For now, limit to max 8 bits, i.e. 256 sub DBs.
    // 0 bits (the default value) disables striping entirely.
    auto n_stripe_bits = std::min(std::max(server_config->contentNodeBucketDbStripeBits, 0), 8);
    ContentBucketDbOptions opts;
    opts.n_stripe_bits = n_stripe_bits;
    return opts;
}

}

ServiceLayerProcess::ServiceLayerProcess(const config::ConfigUri& configUri)
    : Process(configUri),
      _externalVisitors(),
      _node(),
      _storage_chain_builder(),
      _context(std::make_unique<framework::defaultimplementation::RealClock>(),
               bucket_db_options_from_config(configUri))
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
    add_external_visitors();
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

void
ServiceLayerProcess::add_external_visitors()
{
    _externalVisitors["searchvisitor"] = std::make_shared<streaming::SearchVisitorFactory>(_configUri, nullptr, "");
}

} // storage
