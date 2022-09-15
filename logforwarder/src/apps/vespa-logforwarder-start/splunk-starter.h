// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "cf-handler.h"
#include "child-handler.h"
#include <vespa/config-logforwarder.h>

using cloud::config::LogforwarderConfig;

class SplunkStarter : public CfHandler {
private:
    ChildHandler _childHandler;
public:
    SplunkStarter();
    virtual ~SplunkStarter();
    void stop();
    void gotConfig(const LogforwarderConfig& config) override;
};

