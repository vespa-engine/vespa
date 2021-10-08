// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "iconfigholder.h"
#include <mutex>
#include <condition_variable>

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
    std::mutex              _lock;
    std::condition_variable _cond;
    ConfigUpdate::UP        _current;
};

} // namespace config

