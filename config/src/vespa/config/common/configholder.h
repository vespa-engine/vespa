// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    ConfigUpdate::UP provide();
    void handle(ConfigUpdate::UP update);
    bool wait(uint64_t timeoutInMillis);
    bool poll();
    void interrupt();
public:
    vespalib::Monitor _monitor;
    ConfigUpdate::UP _current;
};

} // namespace config

