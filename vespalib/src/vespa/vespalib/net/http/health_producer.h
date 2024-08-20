// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>

namespace vespalib {

struct HealthProducer {
    struct Health {
        bool ok;
        std::string msg;
        Health(bool o, const std::string &m) : ok(o), msg(m) {}
    };
    virtual Health getHealth() const = 0;
    virtual ~HealthProducer() {}
};

} // namespace vespalib

