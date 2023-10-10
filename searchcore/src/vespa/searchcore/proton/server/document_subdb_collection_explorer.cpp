// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_subdb_collection_explorer.h"
#include "document_subdb_explorer.h"

using vespalib::slime::Inserter;

namespace proton {

const vespalib::string READY = "ready";
const vespalib::string REMOVED = "removed";
const vespalib::string NOT_READY = "notready";

namespace {

std::unique_ptr<vespalib::StateExplorer>
createExplorer(const IDocumentSubDB &subDb)
{
    return std::unique_ptr<vespalib::StateExplorer>(new DocumentSubDBExplorer(subDb));
}

}

DocumentSubDBCollectionExplorer::DocumentSubDBCollectionExplorer(const DocumentSubDBCollection &subDbs)
    : _subDbs(subDbs)
{
}

void
DocumentSubDBCollectionExplorer::get_state(const Inserter &inserter, bool full) const
{
    // This is a transparent state where the short state of all children is rendered instead.
    (void) inserter;
    (void) full;
}

std::vector<vespalib::string>
DocumentSubDBCollectionExplorer::get_children_names() const
{
    return {READY, REMOVED, NOT_READY};
}

std::unique_ptr<vespalib::StateExplorer>
DocumentSubDBCollectionExplorer::get_child(vespalib::stringref name) const
{
    if (name == READY) {
        return createExplorer(*_subDbs.getReadySubDB());
    } else if (name == REMOVED) {
        return createExplorer(*_subDbs.getRemSubDB());
    } else if (name == NOT_READY) {
        return createExplorer(*_subDbs.getNotReadySubDB());
    }
    return std::unique_ptr<vespalib::StateExplorer>();
}

} // namespace proton
