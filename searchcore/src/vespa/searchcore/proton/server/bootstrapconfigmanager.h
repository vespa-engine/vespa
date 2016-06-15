// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/config/config.h>
#include <vespa/config/retriever/configsnapshot.h>
#include <vespa/config/retriever/configkeyset.h>
#include "bootstrapconfig.h"

namespace proton {

/**
 * This class handles subscribing to the proton bootstrap config.
 */
class BootstrapConfigManager
{
public:
    BootstrapConfigManager(const vespalib::string & configId);
    const config::ConfigKeySet createConfigKeySet() const;

    BootstrapConfig::SP
    getConfig(void) const
    {
        vespalib::LockGuard lock(_pendingConfigLock);
        return _pendingConfigSnapshot;
    }
    void update(const config::ConfigSnapshot & snapshot);

private:
    BootstrapConfig::SP _pendingConfigSnapshot;
    vespalib::string _configId;
    vespalib::Lock _pendingConfigLock;
};

} // namespace proton

