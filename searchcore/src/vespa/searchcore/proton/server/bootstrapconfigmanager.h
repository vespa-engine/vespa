// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/config/retriever/configkeyset.h>
#include <mutex>

namespace config { class ConfigSnapshot; };
namespace proton {

class BootstrapConfig;

/**
 * This class handles subscribing to the proton bootstrap config.
 */
class BootstrapConfigManager
{
public:
    BootstrapConfigManager(const vespalib::string & configId);
    ~BootstrapConfigManager();
    const config::ConfigKeySet createConfigKeySet() const;

    std::shared_ptr<BootstrapConfig> getConfig() const;
    void update(const config::ConfigSnapshot & snapshot);

private:
    std::shared_ptr<BootstrapConfig> _pendingConfigSnapshot;
    vespalib::string                 _configId;
    mutable std::mutex               _pendingConfigMutex;
};

} // namespace proton

