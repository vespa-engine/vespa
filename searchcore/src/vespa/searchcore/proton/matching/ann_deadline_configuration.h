// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/deadline.h>
#include <vespa/vespalib/util/doom.h>
#include <vespa/vespalib/util/time.h>

namespace proton::matching {

class AnnDeadlineConfiguration {
public:
    AnnDeadlineConfiguration(const vespalib::Doom& now, vespalib::steady_time soft_doom);
    AnnDeadlineConfiguration(const vespalib::Doom& now, vespalib::duration timebudget, bool timeout_enabled, vespalib::steady_time timeout) noexcept;

    const vespalib::Deadline make_ann_deadline(uint32_t remaining_searches) const noexcept;

private:
    const vespalib::Doom& _doom;
    vespalib::duration    _timebudget;
    bool                  _timeout_enabled;
    vespalib::steady_time _timeout;
};


}
