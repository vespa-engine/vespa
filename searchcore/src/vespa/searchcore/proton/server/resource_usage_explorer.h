// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/http/state_explorer.h>

namespace proton {

class ResourceUsageNotifier;
class ResourceUsageTracker;

/**
 * Class used to explore the resource usage of proton.
 */
class ResourceUsageExplorer : public vespalib::StateExplorer
{
private:
    const ResourceUsageNotifier& _usage_notifier;
    const ResourceUsageTracker& _usage_tracker;

public:
    ResourceUsageExplorer(const ResourceUsageNotifier& usage_notifier,
                          const ResourceUsageTracker& usage_tracker);

    void get_state(const vespalib::slime::Inserter &inserter, bool full) const override;
};

} // namespace proton
