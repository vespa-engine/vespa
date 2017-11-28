// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace vespalib {
namespace metrics {

struct DummyTimeSupplier {
    typedef int TimeStamp;
    TimeStamp now_stamp() const { return 0; }
    double stamp_to_s(TimeStamp) const { return 0.0; }
};

} // namespace vespalib::metrics
} // namespace vespalib
