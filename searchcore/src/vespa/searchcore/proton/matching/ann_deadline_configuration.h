// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/deadline.h>
#include <vespa/vespalib/util/doom.h>
#include <vespa/vespalib/util/time.h>

namespace proton::matching {

/**
 * Class that stores the ANN time budget and ANN timeout.
 * Used to create Deadline objects for the individual ANN searches.
 */
class AnnDeadlineConfiguration {
public:
    AnnDeadlineConfiguration(const vespalib::Doom& now, vespalib::steady_time soft_doom);
    AnnDeadlineConfiguration(const vespalib::Doom& now, vespalib::duration timebudget, bool timeout_enabled, vespalib::steady_time timeout) noexcept;

    /**
     * Returns a Deadline for the next ANN search.
     * This deadline is the minimum of the time budget and the time until the timeout,
     * where the time until the timeout is distributed equally between all remaining searches if
     * the timeout is enabled.
     */
    const vespalib::Deadline make_ann_deadline(uint32_t remaining_searches) const noexcept;

private:
    const vespalib::Doom& _doom;
    vespalib::duration    _timebudget;
    bool                  _timeout_enabled;
    vespalib::steady_time _timeout;
};


}
