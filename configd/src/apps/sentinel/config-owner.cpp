// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config-owner.h"
#include <vespa/config/subscription/configsubscriber.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".sentinel.config-owner");

namespace config::sentinel {

ConfigOwner::ConfigOwner() = default;

ConfigOwner::~ConfigOwner() = default;

void
ConfigOwner::subscribe(const std::string & configId, std::chrono::milliseconds timeout) {
    _sentinelHandle = _subscriber.subscribe<SentinelConfig>(configId, timeout);
}

void
ConfigOwner::doConfigure()
{
    _currConfig = _sentinelHandle->getConfig();
    LOG_ASSERT(_currConfig);
    _currGeneration = _subscriber.getGeneration();
    const SentinelConfig& config(*_currConfig);
    const auto & app = config.application;
    LOG(config, "Sentinel got %zd service elements [tenant(%s), application(%s), instance(%s)] for config generation %" PRId64,
        config.service.size(), app.tenant.c_str(), app.name.c_str(), app.instance.c_str(), _currGeneration);
}


// Return true if there was a config generation change
bool
ConfigOwner::checkForConfigUpdate() {
    if (_subscriber.nextGenerationNow()) {
        doConfigure();
        return true;
    }
    return false;
}

}
