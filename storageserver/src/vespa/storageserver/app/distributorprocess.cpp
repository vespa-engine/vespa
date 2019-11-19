// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distributorprocess.h"
#include <vespa/storage/common/storagelink.h>
#include <vespa/config/helper/configgetter.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".process.distributor");

namespace storage {

DistributorProcess::DistributorProcess(const config::ConfigUri & configUri)
    : Process(configUri),
      _activeFlag(DistributorNode::NO_NEED_FOR_ACTIVE_STATES),
      _use_btree_database(false)
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
DistributorProcess::setupConfig(milliseconds subscribeTimeout)
{
    using vespa::config::content::core::StorServerConfig;
    using vespa::config::content::core::StorDistributormanagerConfig;
    using vespa::config::content::core::StorVisitordispatcherConfig;

    auto stor_config = config::ConfigGetter<StorServerConfig>::getConfig(
            _configUri.getConfigId(), _configUri.getContext(), subscribeTimeout);
    if (stor_config->persistenceProvider.type != StorServerConfig::PersistenceProvider::Type::STORAGE) {
        _activeFlag = DistributorNode::NEED_ACTIVE_BUCKET_STATES_SET;
    }
    auto dist_config = config::ConfigGetter<StorDistributormanagerConfig>::getConfig(_configUri.getConfigId(), _configUri.getContext(), subscribeTimeout);
    _use_btree_database = dist_config->useBtreeDatabase;
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
    _node.reset(new DistributorNode(_configUri, _context, *this, _activeFlag, _use_btree_database, StorageLink::UP()));
    _node->handleConfigChange(*_distributorConfigHandler->getConfig());
    _node->handleConfigChange(*_visitDispatcherConfigHandler->getConfig());
}

} // storage
