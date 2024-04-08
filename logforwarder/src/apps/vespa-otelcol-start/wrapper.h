// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "cf-handler.h"
#include "child-handler.h"

#include <string>

class Wrapper : public CfHandler {
private:
    ChildHandler _childHandler;
public:
    Wrapper(const std::string &configId);
    ~Wrapper();
    void check();
    void stop();
    void gotConfig(const OpenTelemetryConfig& config) override;
};
