// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/state_explorer.h>

namespace proton {

class DiskMemUsageFilter;

/**
 * Class used to explore the resource usage of proton.
 */
class ResourceUsageExplorer : public vespalib::StateExplorer
{
private:
    const DiskMemUsageFilter &_usageFilter;

public:
    ResourceUsageExplorer(const DiskMemUsageFilter &usageFilter);

    virtual void get_state(const vespalib::slime::Inserter &inserter, bool full) const override;
};

} // namespace proton
