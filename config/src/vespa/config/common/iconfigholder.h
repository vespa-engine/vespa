// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/common/configupdate.h>
#include <vespa/vespalib/util/time.h>

namespace config {

class IConfigHolder
{
public:
    virtual ~IConfigHolder() = default;
    virtual std::unique_ptr<ConfigUpdate> provide() = 0;
    virtual void handle(std::unique_ptr<ConfigUpdate> obj) = 0;
    virtual void close() = 0;
    virtual bool poll() = 0;
    bool wait_for(vespalib::duration timeout) {
        return wait_until(vespalib::steady_clock::now() + timeout);
    }
    virtual bool wait_until(vespalib::steady_time deadline) = 0;
};

} // namespace config

