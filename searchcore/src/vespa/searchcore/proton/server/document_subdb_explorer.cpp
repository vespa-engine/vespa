// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_subdb_explorer.h"
#include <vespa/searchcore/proton/attribute/attribute_manager_explorer.h>
#include <vespa/searchcore/proton/attribute/attribute_writer_explorer.h>
#include <vespa/searchcore/proton/docsummary/document_store_explorer.h>
#include <vespa/searchcore/proton/documentmetastore/document_meta_store_explorer.h>
#include <vespa/searchcorespi/index/index_manager_explorer.h>

using searchcorespi::IndexManagerExplorer;
using vespalib::slime::Inserter;
using vespalib::StateExplorer;

namespace proton {

namespace {

const std::string DOCUMENT_META_STORE = "documentmetastore";
const std::string DOCUMENT_STORE = "documentstore";
const std::string ATTRIBUTE = "attribute";
const std::string ATTRIBUTE_WRITER = "attributewriter";
const std::string INDEX = "index";

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

std::vector<std::string>
DocumentSubDBExplorer::get_children_names() const
{
    std::vector<std::string> children = {DOCUMENT_META_STORE, DOCUMENT_STORE};
    if (_subDb.getAttributeManager()) {
        children.push_back(ATTRIBUTE);
    }
    if (_subDb.get_attribute_writer()) {
        children.push_back(ATTRIBUTE_WRITER);
    }
    if (_subDb.getIndexManager()) {
        children.push_back(INDEX);
    }
    return children;
}

std::unique_ptr<StateExplorer>
DocumentSubDBExplorer::get_child(std::string_view name) const
{
    if (name == DOCUMENT_META_STORE) {
        // TODO(geirst): Avoid const cast by adding const interface to
        // IDocumentMetaStoreContext as seen from IDocumentSubDB.
        return std::make_unique<DocumentMetaStoreExplorer>(
                (const_cast<IDocumentSubDB &>(_subDb)).getDocumentMetaStoreContext().getReadGuard());
    } else if (name == DOCUMENT_STORE) {
        return std::make_unique<DocumentStoreExplorer>(_subDb.getSummaryManager());
    } else if (name == ATTRIBUTE) {
        auto attrMgr = _subDb.getAttributeManager();
        if (attrMgr) {
            return std::make_unique<AttributeManagerExplorer>(attrMgr);
        }
    } else if (name == ATTRIBUTE_WRITER) {
        auto writer = _subDb.get_attribute_writer();
        if (writer) {
            return std::make_unique<AttributeWriterExplorer>(std::move(writer));
        }
    } else if (name == INDEX) {
        auto idxMgr = _subDb.getIndexManager();
        if (idxMgr) {
            return std::make_unique<IndexManagerExplorer>(std::move(idxMgr));
        }
    }
    return {};
}

}
