// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "iconfigholder.h"
#include <vespa/vespalib/util/sync.h>

namespace config {

/**
 * A config holder contains the latest config object of a subscription.
 */
class ConfigHolder : public IConfigHolder
{
public:
    ConfigHolder();
    ~ConfigHolder() override;

    ConfigUpdate::UP provide() override;
    void handle(ConfigUpdate::UP update) override;
    bool wait(milliseconds timeoutInMillis) override;
    bool poll() override;
    void interrupt() override;
public:
    vespalib::Monitor _monitor;
    ConfigUpdate::UP _current;
};

} // namespace config

