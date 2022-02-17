// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/config-model.h>
#include <vespa/config/subscription/configsubscriber.h>
#include <optional>
#include <mutex>

using cloud::config::ModelConfig;

namespace config::sentinel {

/**
 * Handles config subscription and has a snapshot of current config.
 **/
class ModelOwner {
private:
    std::string _configId;
    config::ConfigSubscriber _subscriber;
    config::ConfigHandle<ModelConfig>::UP _modelHandle;
    std::mutex _lock;
    std::unique_ptr<ModelConfig> _modelConfig;
public:
    ModelOwner(const std::string &configId);
    ~ModelOwner();
    void start(std::chrono::milliseconds timeout, bool firstTime);
    void checkForUpdates();
    std::optional<ModelConfig> getModelConfig();
};

}
