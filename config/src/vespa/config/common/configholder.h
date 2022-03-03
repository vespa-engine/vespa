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

    std::unique_ptr<ConfigUpdate> provide() override;
    void handle(std::unique_ptr<ConfigUpdate> update) override;
    bool wait_until(vespalib::steady_time deadline) override;
    bool poll() override;
    void close() override;
public:
    std::mutex                    _lock;
    std::condition_variable       _cond;
    std::unique_ptr<ConfigUpdate> _current;
};

} // namespace config

