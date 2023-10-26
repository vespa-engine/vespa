// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "health_adapter.h"
#include <vespa/vespalib/util/stringfmt.h>

namespace proton {

HealthAdapter::HealthAdapter(const StatusProducer &sp)
    : _statusProducer(sp)
{
}

vespalib::HealthProducer::Health
HealthAdapter::getHealth() const
{
    bool ok = true;
    vespalib::string msg;
    StatusReport::List reports(_statusProducer.getStatusReports());
    for (size_t i = 0; i < reports.size(); ++i) {
        const StatusReport &r = *reports[i];
        if (r.getState() != StatusReport::UPOK) {
            ok = false;
            if (msg.size() > 0) {
                msg.append(", ");
            }
            msg.append(vespalib::make_string("%s: %s",
                                             r.getComponent().c_str(),
                                             r.getMessage().c_str()));
        }
    }
    if (ok) {
        return Health(true, "All OK");
    } else {
        return Health(false, msg);
    }
}

} // namespace proton
