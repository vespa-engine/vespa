// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_subdb_explorer.h"

#include <vespa/searchcore/proton/attribute/attribute_manager_explorer.h>
#include <vespa/searchcore/proton/documentmetastore/document_meta_store_explorer.h>
#include <vespa/searchcore/proton/docsummary/document_store_explorer.h>
#include <vespa/searchcorespi/index/index_manager_explorer.h>

using searchcorespi::IndexManagerExplorer;
using vespalib::slime::Inserter;
using vespalib::StateExplorer;

namespace proton {

namespace {

const vespalib::string DOCUMENT_META_STORE = "documentmetastore";
const vespalib::string DOCUMENT_STORE = "documentstore";
const vespalib::string ATTRIBUTE = "attribute";
const vespalib::string INDEX = "index";

}

DocumentSubDBExplorer::DocumentSubDBExplorer(const IDocumentSubDB &subDb)
    : _subDb(subDb)
{
}

void
DocumentSubDBExplorer::get_state(const Inserter &inserter, bool full) const
{
    (void) full;
    inserter.insertObject();
}

std::vector<vespalib::string>
DocumentSubDBExplorer::get_children_names() const
{
    std::vector<vespalib::string> children = {DOCUMENT_META_STORE, DOCUMENT_STORE};
    if (_subDb.getAttributeManager().get() != nullptr) {
        children.push_back(ATTRIBUTE);
    }
    if (_subDb.getIndexManager().get() != nullptr) {
        children.push_back(INDEX);
    }
    return children;
}

std::unique_ptr<StateExplorer>
DocumentSubDBExplorer::get_child(vespalib::stringref name) const
{
    if (name == DOCUMENT_META_STORE) {
        // TODO(geirst): Avoid const cast by adding const interface to
        // IDocumentMetaStoreContext as seen from IDocumentSubDB.
        return std::unique_ptr<StateExplorer>(new DocumentMetaStoreExplorer(
                (const_cast<IDocumentSubDB &>(_subDb)).getDocumentMetaStoreContext().getReadGuard()));
    } else if (name == DOCUMENT_STORE) {
        return std::unique_ptr<StateExplorer>(new DocumentStoreExplorer(_subDb.getSummaryManager()));
    } else if (name == ATTRIBUTE) {
        proton::IAttributeManager::SP attrMgr = _subDb.getAttributeManager();
        if (attrMgr.get() != nullptr) {
            return std::unique_ptr<StateExplorer>(new AttributeManagerExplorer(attrMgr));
        }
    } else if (name == INDEX) {
        searchcorespi::IIndexManager::SP idxMgr = _subDb.getIndexManager();
        if (idxMgr.get() != nullptr) {
            return std::unique_ptr<StateExplorer>(new IndexManagerExplorer(std::move(idxMgr)));
        }
    }
    return std::unique_ptr<StateExplorer>();
}

} // namespace proton
