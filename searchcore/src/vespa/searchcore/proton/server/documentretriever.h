// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "documentretrieverbase.h"

namespace search {
class IDocumentMetaStore;
class IAttributeManager;
class IDocumentStore;
namespace index { class Schema; }
}  // namespace search

namespace proton {

class DocumentRetriever : public DocumentRetrieverBase {
public:
    typedef std::vector<std::pair<const document::Field *, vespalib::string>> PositionFields;
    DocumentRetriever(const DocTypeName &docTypeName,
                      const document::DocumentTypeRepo &repo,
                      const search::index::Schema &schema,
                      const IDocumentMetaStoreContext &meta_store,
                      const search::IAttributeManager &attr_manager,
                      const search::IDocumentStore &doc_store);

    document::Document::UP getDocument(search::DocumentIdT lid) const override;
    void visitDocuments(const LidVector & lids, search::IDocumentVisitor & visitor, ReadConsistency) const override;
    void populate(search::DocumentIdT lid, document::Document & doc) const;
private:
    const search::index::Schema     &_schema;
    const search::IAttributeManager &_attr_manager;
    const search::IDocumentStore    &_doc_store;
    PositionFields                   _possiblePositionFields;

    const search::index::Schema & getSchema(void) const override;
    const search::IAttributeManager * getAttrMgr(void) const override;
};

}  // namespace proton

