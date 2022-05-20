// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "idocumentsubdb.h"
#include <vespa/vespalib/net/http/state_explorer.h>

namespace proton {

/**
 * Class used to explore the state of a document sub database.
 */
class DocumentSubDBExplorer : public vespalib::StateExplorer
{
private:
    const IDocumentSubDB &_subDb;

public:
    DocumentSubDBExplorer(const IDocumentSubDB &subDb);

    void get_state(const vespalib::slime::Inserter &inserter, bool full) const override;
    std::vector<vespalib::string> get_children_names() const override;
    std::unique_ptr<StateExplorer> get_child(vespalib::stringref name) const override;
};

} // namespace proton

