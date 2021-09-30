// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/state_explorer.h>

namespace proton {

class DocumentDB;

/**
 * Class used to explore the state of a document database and its components.
 */
class DocumentDBExplorer : public vespalib::StateExplorer
{
private:
    std::shared_ptr<DocumentDB> _docDb;

public:
    DocumentDBExplorer(std::shared_ptr<DocumentDB> docDb);
    ~DocumentDBExplorer() override;

    void get_state(const vespalib::slime::Inserter &inserter, bool full) const override;
    std::vector<vespalib::string> get_children_names() const override;
    std::unique_ptr<vespalib::StateExplorer> get_child(vespalib::stringref name) const override;
};

} // namespace proton
