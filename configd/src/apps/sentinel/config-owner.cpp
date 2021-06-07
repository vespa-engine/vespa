// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config-owner.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/config/common/exceptions.h>
#include <string>

#include <vespa/log/log.h>
LOG_SETUP(".config-owner");

namespace config::sentinel {

ConfigOwner::ConfigOwner() : _subscriber() {}

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

std::unique_ptr<ModelConfig>
ConfigOwner::fetchModelConfig(std::chrono::milliseconds timeout)
{
    std::unique_ptr<ModelConfig> modelConfig;
    ConfigSubscriber tempSubscriber;
    try {
        ConfigHandle<ModelConfig>::UP modelHandle =
            tempSubscriber.subscribe<ModelConfig>("admin/model", timeout);
        if (tempSubscriber.nextGenerationNow()) {
            modelConfig = modelHandle->getConfig();
            LOG(config, "Sentinel got model info [version %s] for %zd hosts [config generation %" PRId64 "]",
                modelConfig->vespaVersion.c_str(), modelConfig->hosts.size(),
                tempSubscriber.getGeneration());
        }
    } catch (ConfigTimeoutException & ex) {
        LOG(warning, "Timeout getting model config: %s [skipping connectivity checks]", ex.getMessage().c_str());
    } catch (InvalidConfigException& ex) {
        LOG(warning, "Invalid model config: %s [skipping connectivity checks]", ex.getMessage().c_str());
    } catch (ConfigRuntimeException& ex) {
        LOG(warning, "Runtime exception getting model config: %s [skipping connectivity checks]", ex.getMessage().c_str());

    }
    return modelConfig;
}

}
