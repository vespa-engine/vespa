// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "model-owner.h"
#include <vespa/config/common/exceptions.h>
#include <vespa/config/subscription/configsubscriber.hpp>
#include <cinttypes>

#include <vespa/log/log.h>
LOG_SETUP(".sentinel.model-owner");

using namespace std::chrono_literals;

namespace config::sentinel {

std::optional<ModelConfig> ModelOwner::getModelConfig() {
    std::lock_guard<std::mutex> guard(_lock);
    if (_modelConfig) {
        return ModelConfig(*_modelConfig);
    } else {
        return {};
    }
}


ModelOwner::ModelOwner(const std::string &configId)
  : _configId(configId)
{}

ModelOwner::~ModelOwner() = default;

void
ModelOwner::start(std::chrono::milliseconds timeout, bool firstTime) {
    try {
        _modelHandle =_subscriber.subscribe<ModelConfig>(_configId, timeout);
    } catch (ConfigTimeoutException & ex) {
        if (firstTime) {
            LOG(warning, "Timeout getting model config: %s [skipping connectivity checks]", ex.message());
        }
    } catch (InvalidConfigException& ex) {
        if (firstTime) {
            LOG(warning, "Invalid model config: %s [skipping connectivity checks]", ex.message());
        }
    } catch (ConfigRuntimeException& ex) {
        if (firstTime) {
            LOG(warning, "Runtime exception getting model config: %s [skipping connectivity checks]", ex.message());
        }
    }
}

void
ModelOwner::checkForUpdates() {
    if (! _modelHandle) {
        start(250ms, false);
    }
    if (_modelHandle && _subscriber.nextGenerationNow()) {
        if (auto newModel = _modelHandle->getConfig()) {
            LOG(config, "Sentinel got model info [version %s] for %zd hosts [config generation %" PRId64 "]",
                newModel->vespaVersion.c_str(), newModel->hosts.size(), _subscriber.getGeneration());
            std::lock_guard<std::mutex> guard(_lock);
            _modelConfig = std::move(newModel);
        }
    }
}

}
