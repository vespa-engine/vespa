// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class ConfigKeeper
 * \class memfile
 *
 * \brief Utility function for live reconfiguration
 *
 * When many threads want the same config, we don't want each of these threads
 * to subscribe on the same config because of the following reasons:
 *   - No need to put lots of extra load on the config system.
 *   - Application doesn't know whether all users have the same config version
 *     at any given time.
 *
 * This class implements a utility class for handling this.
 */
#pragma once

#include <vespa/vespalib/util/sync.h>

namespace storage {

template<typename ConfigClass>
class ConfigKeeper {
    vespalib::Monitor _configLock;
    bool _configUpdated; // Set to true if updating config.
    std::unique_ptr<ConfigClass> _nextConfig;
    ConfigClass _config;

public:
    ConfigKeeper() : _configUpdated(false) {}

    void updateConfig(const ConfigClass& config) {
        vespalib::MonitorGuard lock(_configLock);
        _nextConfig.reset(new ConfigClass(config));
        _configUpdated = true;
    }

    void activateNewConfig() {
        if (!_configUpdated) return;
        vespalib::MonitorGuard lock(_configLock);
        _config = *_nextConfig;
        _nextConfig.reset(0);
        _configUpdated = false;
        lock.signal();
    }

    void waitForAnyActivation() {
        vespalib::MonitorGuard lock(_configLock);
        while (_configUpdated) lock.wait();
    }

    ConfigClass* operator->() { return &_config; }
    ConfigClass& operator*() { return _config; }
};

} // storage

