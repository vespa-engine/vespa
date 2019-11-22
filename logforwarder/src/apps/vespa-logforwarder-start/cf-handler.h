// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/config.h>
#include <vespa/config-logforwarder.h>
#include "child-handler.h"

using cloud::config::LogforwarderConfig;

class CfHandler {
private:
    ChildHandler childHandler;
    config::ConfigSubscriber _subscriber;
    config::ConfigHandle<LogforwarderConfig>::UP _handle;
    void subscribe(const std::string & configId, std::chrono::milliseconds timeout);
    void doConfigure();
public:
    CfHandler();
    virtual ~CfHandler();
    void start(const char *configId);
    void check();
};
