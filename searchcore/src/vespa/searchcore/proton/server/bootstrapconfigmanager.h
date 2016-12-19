// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/config/retriever/configkeyset.h>
#include <vespa/vespalib/util/sync.h>

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

    std::shared_ptr<BootstrapConfig>
    getConfig() const
    {
        vespalib::LockGuard lock(_pendingConfigLock);
        return _pendingConfigSnapshot;
    }
    void update(const config::ConfigSnapshot & snapshot);

private:
    std::shared_ptr<BootstrapConfig> _pendingConfigSnapshot;
    vespalib::string                 _configId;
    vespalib::Lock                   _pendingConfigLock;
};

} // namespace proton

