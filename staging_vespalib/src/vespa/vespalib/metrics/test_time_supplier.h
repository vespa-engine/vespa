// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstddef>

namespace vespalib {
namespace metrics {

class TestTimeSupplier {
private:
    mutable size_t _cnt;
public:
    typedef size_t TimeStamp;
    TimeStamp now_stamp() const { return ++_cnt; }
    double stamp_to_s(TimeStamp stamp) const { return (double)stamp; }
    TestTimeSupplier() : _cnt(0) {}
};

} // namespace vespalib::metrics
} // namespace vespalib
