// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <chrono>

namespace vespalib {
namespace metrics {

class WallclockTimeSupplier {
private:
    using Clock = std::chrono::system_clock;
    using seconds = std::chrono::duration<double>;
public:
    typedef Clock::time_point TimeStamp;
    TimeStamp now_stamp() const { return Clock::now(); }
    double stamp_to_s(TimeStamp stamp) const {
        seconds s = stamp.time_since_epoch();
        return s.count();
    }
};

} // namespace vespalib::metrics
} // namespace vespalib
