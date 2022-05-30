// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "documentsubdbcollection.h"
#include <vespa/vespalib/net/http/state_explorer.h>

namespace proton {

/**
 * Class used to explore the state of a collection of document sub databases.
 */
class DocumentSubDBCollectionExplorer : public vespalib::StateExplorer
{
private:
    const DocumentSubDBCollection &_subDbs;

public:
    DocumentSubDBCollectionExplorer(const DocumentSubDBCollection &subDbs);

    void get_state(const vespalib::slime::Inserter &inserter, bool full) const override;
    std::vector<vespalib::string> get_children_names() const override;
    std::unique_ptr<StateExplorer> get_child(vespalib::stringref name) const override;
};

} // namespace proton

