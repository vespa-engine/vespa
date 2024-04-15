// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "file-watcher.h"
#include <vespa/config-open-telemetry.h>
#include <vespa/config/subscription/configsubscriber.h>

using cloud::config::OpenTelemetryConfig;

class CfHandler {
private:
    FileWatcher _fileWatcher;
    config::ConfigSubscriber _subscriber;
    config::ConfigHandle<OpenTelemetryConfig>::UP _handle = {};
    std::unique_ptr<OpenTelemetryConfig> _lastConfig = {};
    void subscribe(const std::string & configId, std::chrono::milliseconds timeout);
    void doConfigure();
public:
    CfHandler(const std::string &configId);
    virtual ~CfHandler();
    void start(const std::string &configId);
    void checkConfig();
    virtual void gotConfig(const OpenTelemetryConfig&) = 0;
};
