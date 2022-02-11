// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/config-sentinel.h>
#include <vespa/config-model.h>
#include <vespa/config/subscription/configsubscriber.h>

using cloud::config::SentinelConfig;

namespace config::sentinel {

/**
 * Handles config subscription and has a snapshot of current config.
 **/
class ConfigOwner {
private:
    config::ConfigSubscriber _subscriber;
    config::ConfigHandle<SentinelConfig>::UP _sentinelHandle;
    
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
};

}
