// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ann_deadline_configuration.h"
#include <cassert>

using vespalib::Deadline;
using vespalib::Doom;

namespace proton::matching {

AnnDeadlineConfiguration::AnnDeadlineConfiguration(vespalib::steady_time soft_doom)
    : AnnDeadlineConfiguration(vespalib::duration::max(), false, soft_doom) {
}

AnnDeadlineConfiguration::AnnDeadlineConfiguration(vespalib::duration timebudget, bool timeout_enabled, vespalib::steady_time timeout) noexcept
    : _timebudget(timebudget),
      _timeout_enabled(timeout_enabled),
      _timeout(timeout) {
}

const vespalib::Deadline AnnDeadlineConfiguration::make_ann_deadline(const vespalib::Doom& doom, uint32_t remaining_searches) const noexcept {
    assert(remaining_searches > 0);
    vespalib::steady_time now(doom.getTimeNS());
    // ANN might hit the deadline due to a depleted time budget or a timeout,
    // whatever happens first. The timeout might be the ANN-specific timeout
    // or the soft-timeout. The soft-timeout is used when ANN timeouts
    // are not enabled, and in this case, reaching the soft-timeout
    // is not reported as an ANN timeout.
    vespalib::duration timeout_left = _timeout_enabled ? ((_timeout - now) / remaining_searches)
                                                       : _timeout - now;

    if (_timebudget < timeout_left) {
        return doom.make_deadline(now + _timebudget, Deadline::Type::BUDGET);
    } else {
        return doom.make_deadline(now + timeout_left, _timeout_enabled ? Deadline::Type::TIMEOUT : Deadline::Type::BUDGET);
    }
}

}
