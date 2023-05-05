// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config-logforwarder.h>
#include <vespa/config/subscription/configsubscriber.h>

using cloud::config::LogforwarderConfig;

class CfHandler {
private:
    config::ConfigSubscriber _subscriber;
    config::ConfigHandle<LogforwarderConfig>::UP _handle;
    std::unique_ptr<LogforwarderConfig> _lastConfig;
    time_t _lastCertFileChange = 0;
    void subscribe(const std::string & configId, std::chrono::milliseconds timeout);
    void doConfigure();
    bool certFileChanged();
public:
    CfHandler();
    virtual ~CfHandler();
    vespalib::string clientCertFile() const;
    vespalib::string clientKeyFile() const;
    void start(const char *configId);
    void check();

    virtual void gotConfig(const LogforwarderConfig&) = 0;
};
