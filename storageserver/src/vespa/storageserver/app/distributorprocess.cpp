// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distributorprocess.h"
#include <vespa/config/helper/configgetter.hpp>
#include <vespa/storage/common/bucket_stripe_utils.h>
#include <vespa/storage/common/i_storage_chain_builder.h>
#include <vespa/storage/common/storagelink.h>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".process.distributor");

namespace storage {

DistributorProcess::DistributorProcess(const config::ConfigUri & configUri)
    : Process(configUri),
      _context(),
      _num_distributor_stripes(0), // TODO STRIPE: change default when legacy single stripe mode is removed
      _node(),
      _distributorConfigHandler(),
      _visitDispatcherConfigHandler(),
      _storage_chain_builder()
{
}

DistributorProcess::~DistributorProcess() {
    shutdown();
}

void
DistributorProcess::shutdown()
{
    Process::shutdown();
    _node.reset();
}

namespace {

uint32_t
adjusted_num_distributor_stripes(int32_t cfg_n_stripes)
{
    if (cfg_n_stripes <= 0) {
        uint32_t cpu_cores = std::thread::hardware_concurrency();
        return storage::tune_num_stripes_based_on_cpu_cores(cpu_cores);
    } else {
        uint32_t adjusted_n_stripes = storage::adjusted_num_stripes(cfg_n_stripes);
        if (adjusted_n_stripes != static_cast<uint32_t>(cfg_n_stripes)) {
            LOG(warning, "Configured number of distributor stripes (%d) is not valid. Adjusting to a valid value (%u)",
                cfg_n_stripes, adjusted_n_stripes);
        }
        return adjusted_n_stripes;
    }
}

}

void
DistributorProcess::setupConfig(vespalib::duration subscribeTimeout)
{
    using vespa::config::content::core::StorDistributormanagerConfig;
    using vespa::config::content::core::StorVisitordispatcherConfig;

    auto distr_cfg = config::ConfigGetter<StorDistributormanagerConfig>::getConfig(
            _configUri.getConfigId(), _configUri.getContext(), subscribeTimeout);
    _num_distributor_stripes = adjusted_num_distributor_stripes(distr_cfg->numDistributorStripes);
    _distributorConfigHandler = _configSubscriber.subscribe<StorDistributormanagerConfig>(_configUri.getConfigId(), subscribeTimeout);
    _visitDispatcherConfigHandler = _configSubscriber.subscribe<StorVisitordispatcherConfig>(_configUri.getConfigId(), subscribeTimeout);
    Process::setupConfig(subscribeTimeout);
}

void
DistributorProcess::updateConfig()
{
    Process::updateConfig();
    if (_distributorConfigHandler->isChanged()) {
        _node->handleConfigChange(*_distributorConfigHandler->getConfig());
    }
    if (_visitDispatcherConfigHandler->isChanged()) {
        _node->handleConfigChange(*_visitDispatcherConfigHandler->getConfig());
    }
}

bool
DistributorProcess::configUpdated()
{
    bool changed = Process::configUpdated();
    if (_distributorConfigHandler->isChanged()) {
        LOG(info, "Distributor manager config detected changed");
        changed = true;
    }
    if (_visitDispatcherConfigHandler->isChanged()) {
        LOG(info, "Visitor dispatcher config detected changed");
        changed = true;
    }
    return changed;
}

void
DistributorProcess::createNode()
{
    _node = std::make_unique<DistributorNode>(_configUri, _context, *this, _num_distributor_stripes, StorageLink::UP(), std::move(_storage_chain_builder));
    _node->handleConfigChange(*_distributorConfigHandler->getConfig());
    _node->handleConfigChange(*_visitDispatcherConfigHandler->getConfig());
}

void
DistributorProcess::set_storage_chain_builder(std::unique_ptr<IStorageChainBuilder> builder)
{
    _storage_chain_builder = std::move(builder);
}

} // storage
