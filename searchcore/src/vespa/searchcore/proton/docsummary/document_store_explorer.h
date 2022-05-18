// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "isummarymanager.h"
#include <vespa/vespalib/net/http/state_explorer.h>

namespace proton {

/**
 * Class used to explore the state of a document store.
 */
class DocumentStoreExplorer : public vespalib::StateExplorer
{
private:
    ISummaryManager::SP _mgr;

public:
    DocumentStoreExplorer(ISummaryManager::SP mgr);

    void get_state(const vespalib::slime::Inserter &inserter, bool full) const override;
};

} // namespace proton

