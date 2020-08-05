// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "documentretrieverbase.h"
#include <vespa/vespalib/stllike/hash_map.h>

namespace search {
    struct IDocumentMetaStore;
    class IAttributeManager;
    class IDocumentStore;
}
namespace search::index { class Schema; }

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
    ~DocumentRetriever() override;

    document::Document::UP getDocumentByLidOnly(search::DocumentIdT lid) const override;
    void visitDocuments(const LidVector & lids, search::IDocumentVisitor & visitor, ReadConsistency) const override;
    DocumentUP getDocument(search::DocumentIdT lid, const document::DocumentId &, const document::FieldSet &) const override;
    void populate(search::DocumentIdT lid, document::Document & doc) const;
    bool needFetchFromDocStore(const document::FieldSet &) const;
private:
    using FieldSetAttributeMap = vespalib::hash_map<uint64_t, bool>;
    bool needFetchFromDocStore(uint64_t key, const document::Field &) const;
    bool needFetchFromDocStore(uint64_t key, const document::Field::Set &) const;
    void populate(search::DocumentIdT lid, document::Document & doc, const document::Field::Set & attributeFields) const;

    bool isFieldAttribute(const document::Field & field) const;
    const search::index::Schema     &_schema;
    const search::IAttributeManager &_attr_manager;
    const search::IDocumentStore    &_doc_store;
    PositionFields                   _possiblePositionFields;
    document::Field::Set             _attributeFields;
    bool                             _areAllFieldsAttributes;
    mutable FieldSetAttributeMap     _isFieldSetAttributeOnly;
    mutable std::mutex               _lock;

    const search::IAttributeManager * getAttrMgr() const override;
};

}  // namespace proton

