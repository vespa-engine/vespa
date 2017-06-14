// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memfileservicelayerprocess.h"

#include <vespa/log/log.h>
LOG_SETUP(".process.servicelayer");

namespace storage {

// MemFileServiceLayerProcess implementation

MemFileServiceLayerProcess::MemFileServiceLayerProcess(
        const config::ConfigUri & configUri)
    : ServiceLayerProcess(configUri),
      _changed(false)
{
}

void
MemFileServiceLayerProcess::shutdown()
{
    ServiceLayerProcess::shutdown();
    _provider.reset(0);
}

void
MemFileServiceLayerProcess::setupConfig(uint64_t subscribeTimeout)
{
    ServiceLayerProcess::setupConfig(subscribeTimeout);
    _configFetcher.reset(new config::ConfigFetcher(_configUri.getContext()));
    _configFetcher->subscribe<vespa::config::storage::StorDevicesConfig>(_configUri.getConfigId(), this, subscribeTimeout);
    _configFetcher->subscribe<vespa::config::storage::StorMemfilepersistenceConfig>(_configUri.getConfigId(), this, subscribeTimeout);
    _configFetcher->subscribe<vespa::config::content::PersistenceConfig>(_configUri.getConfigId(), this, subscribeTimeout);
    _configFetcher->start();
}

void
MemFileServiceLayerProcess::removeConfigSubscriptions()
{
    _configFetcher.reset(0);
}

void
MemFileServiceLayerProcess::setupProvider()
{
    _provider.reset(new memfile::MemFilePersistenceProvider(
            _context.getComponentRegister(), _configUri));
    _provider->setDocumentRepo(*getTypeRepo());
}

bool
MemFileServiceLayerProcess::configUpdated()
{
    if (ServiceLayerProcess::configUpdated()) return true;
    vespalib::LockGuard guard(_lock);
    return _changed;
}

void
MemFileServiceLayerProcess::updateConfig()
{
    ServiceLayerProcess::updateConfig();
    LOG(info, "Config updated. Sending new config to memfile provider");
    vespalib::LockGuard guard(_lock);
    if (_changed) {
        LOG(debug, "Memfile or device config changed too.");
        if (_nextMemfilepersistence) {
            _provider->setConfig(std::move(_nextMemfilepersistence));
        }
        if (_nextPersistence) {
            _provider->setConfig(std::move(_nextPersistence));
        }
        if (_nextDevices) {
            _provider->setConfig(std::move(_nextDevices));
        }
    }
    _provider->setDocumentRepo(*getTypeRepo());
    _changed = false;
}

void
MemFileServiceLayerProcess::configure(
        std::unique_ptr<vespa::config::storage::StorMemfilepersistenceConfig> config)
{
    vespalib::LockGuard guard(_lock);
    _nextMemfilepersistence = std::move(config);
    _changed = true;
}

void
MemFileServiceLayerProcess::configure(
        std::unique_ptr<vespa::config::content::PersistenceConfig> config)
{
    vespalib::LockGuard guard(_lock);
    _nextPersistence = std::move(config);
    _changed = true;
}

void
MemFileServiceLayerProcess::configure(std::unique_ptr<vespa::config::storage::StorDevicesConfig> config)
{
    vespalib::LockGuard guard(_lock);
    _nextDevices = std::move(config);
    _changed = true;
}

} // storage
