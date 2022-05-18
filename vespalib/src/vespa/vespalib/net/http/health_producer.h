// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib {

struct HealthProducer {
    struct Health {
        bool ok;
        vespalib::string msg;
        Health(bool o, const vespalib::string &m) : ok(o), msg(m) {}
    };
    virtual Health getHealth() const = 0;
    virtual ~HealthProducer() {}
};

} // namespace vespalib

