// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/config/retriever/configkeyset.h>
#include <mutex>
#include <memory>

namespace config { class ConfigSnapshot; };
namespace proton {

class BootstrapConfig;

/**
 * This class handles subscribing to the proton bootstrap config.
 */
class BootstrapConfigManager
{
public:
    BootstrapConfigManager(const std::string & configId);
    ~BootstrapConfigManager();
    const config::ConfigKeySet createConfigKeySet() const;

    std::shared_ptr<BootstrapConfig> getConfig() const;
    void update(const config::ConfigSnapshot & snapshot);

private:
    std::shared_ptr<BootstrapConfig> _pendingConfigSnapshot;
    std::string                 _configId;
    mutable std::mutex               _pendingConfigMutex;
};

} // namespace proton

