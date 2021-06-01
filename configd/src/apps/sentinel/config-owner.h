// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/config-sentinel.h>
#include <vespa/config-model.h>
#include <vespa/config/config.h>

using cloud::config::SentinelConfig;
using cloud::config::ModelConfig;

using config::ConfigSubscriber;
using config::ConfigHandle;

namespace config::sentinel {

/**
 * Handles config subscription and has a snapshot of current config.
 **/
class ConfigOwner {
private:
    ConfigSubscriber _subscriber;
    ConfigHandle<SentinelConfig>::UP _sentinelHandle;

    int64_t _currGeneration = -1;
    std::unique_ptr<SentinelConfig> _currConfig;

    ConfigOwner(const ConfigOwner&) = delete;
    ConfigOwner& operator =(const ConfigOwner&) = delete;

    void doConfigure();
public:
    ConfigOwner();
    virtual ~ConfigOwner();
    void subscribe(const std::string & configId, std::chrono::milliseconds timeout);
    bool checkForConfigUpdate();
    bool hasConfig() const { return _currConfig.get() != nullptr; }
    const SentinelConfig& getConfig() const { return *_currConfig; }
    int64_t getGeneration() const { return _currGeneration; }
    static std::unique_ptr<ModelConfig> fetchModelConfig(std::chrono::milliseconds timeout);
};

}
