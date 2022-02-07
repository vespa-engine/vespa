// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "child-handler.h"
#include <vespa/config-logforwarder.h>
#include <vespa/config/subscription/configsubscriber.h>

using cloud::config::LogforwarderConfig;

class CfHandler {
private:
    ChildHandler _childHandler;
    config::ConfigSubscriber _subscriber;
    config::ConfigHandle<LogforwarderConfig>::UP _handle;
    void subscribe(const std::string & configId, std::chrono::milliseconds timeout);
    void doConfigure();
public:
    CfHandler();
    virtual ~CfHandler();
    void start(const char *configId);
    void stop();
    void check();
};
