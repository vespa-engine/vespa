// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "documentdb.h"
#include <vespa/vespalib/net/state_explorer.h>

namespace proton {

/**
 * Class used to explore the state of a document database and its components.
 */
class DocumentDBExplorer : public vespalib::StateExplorer
{
private:
    DocumentDB::SP _docDb;

public:
    DocumentDBExplorer(const DocumentDB::SP &docDb);
    ~DocumentDBExplorer();

    void get_state(const vespalib::slime::Inserter &inserter, bool full) const override;
    std::vector<vespalib::string> get_children_names() const override;
    std::unique_ptr<vespalib::StateExplorer> get_child(vespalib::stringref name) const override;
};

} // namespace proton
