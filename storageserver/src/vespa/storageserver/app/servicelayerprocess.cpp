// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

ContentBucketDbOptions
bucket_db_options_from_config(const config::ConfigUri& config_uri) {
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

ServiceLayerProcess::ServiceLayerProcess(const config::ConfigUri& configUri, const vespalib::HwInfo& hw_info)
    : Process(configUri),
      _externalVisitors(),
      _persistence_cfg_handle(),
      _visitor_cfg_handle(),
      _filestor_cfg_handle(),
      _node(),
      _storage_chain_builder(),
      _hw_info(hw_info),
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
ServiceLayerProcess::setupConfig(vespalib::duration subscribe_timeout)
{
    _persistence_cfg_handle = _configSubscriber.subscribe<PersistenceConfig>(_configUri.getConfigId(), subscribe_timeout);
    _visitor_cfg_handle     = _configSubscriber.subscribe<StorVisitorConfig>(_configUri.getConfigId(), subscribe_timeout);
    _filestor_cfg_handle    = _configSubscriber.subscribe<StorFilestorConfig>(_configUri.getConfigId(), subscribe_timeout);
    // We reuse the StorServerConfig subscription from the parent Process
    Process::setupConfig(subscribe_timeout);
}

void
ServiceLayerProcess::updateConfig()
{
    Process::updateConfig();
    if (_server_cfg_handle->isChanged()) {
        _node->on_configure(*_server_cfg_handle->getConfig());
    }
    if (_persistence_cfg_handle->isChanged()) {
        _node->on_configure(*_persistence_cfg_handle->getConfig());
    }
    if (_visitor_cfg_handle->isChanged()) {
        _node->on_configure(*_visitor_cfg_handle->getConfig());
    }
    if (_filestor_cfg_handle->isChanged()) {
        _node->on_configure(*_filestor_cfg_handle->getConfig());
    }
}

bool
ServiceLayerProcess::configUpdated()
{
    return Process::configUpdated();
}

void
ServiceLayerProcess::createNode()
{
    add_external_visitors();
    setupProvider();

    StorageNode::BootstrapConfigs bc;
    bc.bucket_spaces_cfg = _bucket_spaces_cfg_handle->getConfig();
    bc.bouncer_cfg       = _bouncer_cfg_handle->getConfig();
    bc.comm_mgr_cfg      = _comm_mgr_cfg_handle->getConfig();
    bc.distribution_cfg  = _distribution_cfg_handle->getConfig();
    bc.server_cfg        = _server_cfg_handle->getConfig();

    ServiceLayerNode::ServiceLayerBootstrapConfigs sbc;
    sbc.storage_bootstrap_configs = std::move(bc);
    sbc.persistence_cfg = _persistence_cfg_handle->getConfig();
    sbc.visitor_cfg     = _visitor_cfg_handle->getConfig();
    sbc.filestor_cfg    = _filestor_cfg_handle->getConfig();

    _node = std::make_unique<ServiceLayerNode>(_configUri, _context, _hw_info, std::move(sbc),
                                               *this, getProvider(), _externalVisitors);
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
