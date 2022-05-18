// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "iindexmanager.h"
#include <vespa/vespalib/net/http/state_explorer.h>

namespace searchcorespi {

/**
 * Class used to explore the state of an index manager.
 */
class IndexManagerExplorer : public vespalib::StateExplorer
{
private:
    IIndexManager::SP _mgr;

public:
    IndexManagerExplorer(IIndexManager::SP mgr);

    void get_state(const vespalib::slime::Inserter &inserter, bool full) const override;
};

} // namespace searchcorespi
