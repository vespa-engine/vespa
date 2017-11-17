// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <mutex>
#include "no_realloc_bunch.h"
#include <vespa/vespalib/stllike/string.h>

namespace vespalib {
namespace metrics {

class NameCollection {
private:
    NoReallocBunch<vespalib::string> _names;
    mutable std::mutex _lock;
public:
    const vespalib::string &lookup(int idx) const;
    int lookup(const vespalib::string& name) const;
    int resolve(const vespalib::string& name);
    size_t size() const;
};

} // namespace vespalib::metrics
} // namespace vespalib
