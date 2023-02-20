// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "backoff.h"
#include <vespa/vespalib/util/signalhandler.h>

namespace slobrok::api {

namespace {
constexpr size_t num_warn_intervals = 5;
const double warn_intervals[num_warn_intervals] = { 1.0, 10.0, 60.0, 600.0, 3600.0 };
}

BackOff::BackOff() { reset(); }

void BackOff::reset() {
    _time = 0.0;
    _since_last_warn = 0.0;
    _nextwarn_idx = 0;
}

double BackOff::get() {
        _since_last_warn += _time;
        if (_time < 20.0) {
            _time += 0.5;
        }
        return _time;
}

bool BackOff::shouldWarn() {
    if (vespalib::SignalHandler::TERM.check()) {
        return false;
    }
    if (_since_last_warn >= warn_intervals[_nextwarn_idx]) {
        if (_nextwarn_idx + 1 < num_warn_intervals) {
            ++_nextwarn_idx;
        }
        _since_last_warn = 0.0;
        return true;
    }
    return false;
}

} // namespace slobrok::api
