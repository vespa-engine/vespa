// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "cf-handler.h"
#include <vespa/config-logforwarder.h>

using cloud::config::LogforwarderConfig;

class SplunkStopper : public CfHandler {
public:
    SplunkStopper(const char *cfid);
    virtual ~SplunkStopper();
    void gotConfig(const LogforwarderConfig& config) override;
};
