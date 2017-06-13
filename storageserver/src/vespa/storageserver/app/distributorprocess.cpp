// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storageserver/app/distributorprocess.h>
#include <vespa/config/helper/configgetter.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".process.distributor");

namespace storage {

DistributorProcess::DistributorProcess(const config::ConfigUri & configUri)
    : Process(configUri),
      _activeFlag(DistributorNode::NO_NEED_FOR_ACTIVE_STATES)
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

void
DistributorProcess::setupConfig(uint64_t subscribeTimeout)
{
    std::unique_ptr<vespa::config::content::core::StorServerConfig> config =
        config::ConfigGetter<vespa::config::content::core::StorServerConfig>::getConfig(_configUri.getConfigId(), _configUri.getContext(), subscribeTimeout);
    if (config->persistenceProvider.type
        != vespa::config::content::core::StorServerConfig::PersistenceProvider::STORAGE)
    {
        _activeFlag = DistributorNode::NEED_ACTIVE_BUCKET_STATES_SET;
    }
    _distributorConfigHandler
            = _configSubscriber.subscribe<vespa::config::content::core::StorDistributormanagerConfig>(
                    _configUri.getConfigId(), subscribeTimeout);
    _visitDispatcherConfigHandler
            = _configSubscriber.subscribe<vespa::config::content::core::StorVisitordispatcherConfig>(
                    _configUri.getConfigId(), subscribeTimeout);
    Process::setupConfig(subscribeTimeout);
}

void
DistributorProcess::updateConfig()
{
    Process::updateConfig();
    if (_distributorConfigHandler->isChanged()) {
        _node->handleConfigChange(
                *_distributorConfigHandler->getConfig());
    }
    if (_visitDispatcherConfigHandler->isChanged()) {
        _node->handleConfigChange(
                *_visitDispatcherConfigHandler->getConfig());
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
    _node.reset(new DistributorNode(_configUri, _context, *this, _activeFlag));
    _node->handleConfigChange(
            *_distributorConfigHandler->getConfig());
    _node->handleConfigChange(
            *_visitDispatcherConfigHandler->getConfig());
}

} // storage
