// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/config-model.h>
#include <vespa/config/config.h>
#include <optional>

using cloud::config::ModelConfig;

namespace config::sentinel {

/**
 * Handles config subscription and has a snapshot of current config.
 **/
class ModelSubscriber {
private:
    std::string _configId;
    config::ConfigSubscriber _subscriber;
    config::ConfigHandle<ModelConfig>::UP _modelHandle;
    std::unique_ptr<ModelConfig> _modelConfig;
public:
    ModelSubscriber(const std::string &configId);
    virtual ~ModelSubscriber();
    void start(std::chrono::milliseconds timeout);
    void checkForUpdates();
    std::optional<ModelConfig> getModelConfig();
};

}
