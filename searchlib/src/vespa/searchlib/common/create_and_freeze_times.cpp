// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "create_and_freeze_times.h"

#include "fileheadertags.h"

#include <vespa/vespalib/data/fileheader.h>

#include <algorithm>

using search::tags::CREATE_TIME;
using search::tags::FREEZE_TIME;
using std::chrono::microseconds;
using std::chrono::steady_clock;
using std::chrono::system_clock;
#if !defined(_LIBCPP_VERSION)
using std::chrono::utc_clock;
#endif

namespace search::common {

namespace {

// Handle low resolution steady clock
steady_clock::duration min_flush_duration =
    std::max(duration_cast<steady_clock::duration>(microseconds(1u)), steady_clock::duration(1u));

} // namespace

CreateAndFreezeTimes::CreateAndFreezeTimes(const vespalib::GenericHeader& header) : CreateAndFreezeTimes() {
    if (header.hasTag(CREATE_TIME) && header.hasTag(FREEZE_TIME)) {
        auto create_time = header.getTag(CREATE_TIME).asInteger();
        auto freeze_time = header.getTag(FREEZE_TIME).asInteger();
        if (freeze_time >= create_time) {
            _create_time = from_utc_us(create_time);
            _freeze_time = from_utc_us(freeze_time);
        }
    }
}

int64_t CreateAndFreezeTimes::to_utc_us(std::chrono::system_clock::time_point system_time) {
#if defined(_LIBCPP_VERSION)
    auto utc_time = system_time;
#else
    auto utc_time = utc_clock::from_sys(system_time);
#endif
    return duration_cast<microseconds>(utc_time.time_since_epoch()).count();
}

system_clock::time_point CreateAndFreezeTimes::from_utc_us(uint64_t us) {
#if defined(_LIBCPP_VERSION)
    return system_clock::time_point(microseconds(us));
#else
    auto utc_time = utc_clock::time_point(microseconds(us));
    return utc_clock::to_sys(utc_time);
#endif
}

void CreateAndFreezeTimes::merge(const CreateAndFreezeTimes& rhs) noexcept {
    if (rhs.valid()) {
        if (valid()) {
            _create_time = std::min(_create_time, rhs._create_time);
            _freeze_time = std::max(_freeze_time, rhs._freeze_time);
        } else {
            _create_time = rhs._create_time;
            _freeze_time = rhs._freeze_time;
        }
    }
}

steady_clock::duration CreateAndFreezeTimes::get_flush_duration() const {
    if (valid() && _freeze_time >= _create_time) {
        return std::max(duration_cast<steady_clock::duration>(_freeze_time - _create_time), min_flush_duration);
    }
    return steady_clock::duration::zero();
}

steady_clock::duration CreateAndFreezeTimes::make_flush_duration(const steady_clock::time_point& create_time) {
    steady_clock::duration flush_duration = steady_clock::now() - create_time;
    return std::max(flush_duration, min_flush_duration);
}

} // namespace search::common
