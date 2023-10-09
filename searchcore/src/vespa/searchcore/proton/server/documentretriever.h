// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

struct IFieldInfo {
    virtual ~IFieldInfo() = default;
    virtual bool isFieldAttribute(const document::Field & field) const = 0;
};

class FieldSetAttributeDB {
public:
    FieldSetAttributeDB(const IFieldInfo & fieldInfo);
    ~FieldSetAttributeDB();
    bool areAllFieldsAttributes(uint64_t key, const document::Field::Set & set) const;
private:
    using FieldSetAttributeMap = vespalib::hash_map<uint64_t, bool>;
    const IFieldInfo               & _fieldInfo;
    mutable FieldSetAttributeMap     _isFieldSetAttributeOnly;
    mutable std::mutex               _lock;
};

class DocumentRetriever : public DocumentRetrieverBase,
                          public IFieldInfo {
public:
    using PositionFields = std::vector<std::pair<const document::Field *, vespalib::string>>;
    DocumentRetriever(const DocTypeName &docTypeName,
                      const document::DocumentTypeRepo &repo,
                      const search::index::Schema &schema,
                      const IDocumentMetaStoreContext &meta_store,
                      const search::IAttributeManager &attr_manager,
                      const search::IDocumentStore &doc_store);
    ~DocumentRetriever() override;

    document::Document::UP getFullDocument(search::DocumentIdT lid) const override;
    void visitDocuments(const LidVector & lids, search::IDocumentVisitor & visitor, ReadConsistency) const override;
    DocumentUP getPartialDocument(search::DocumentIdT lid, const document::DocumentId &, const document::FieldSet &) const override;
    void populate(search::DocumentIdT lid, document::Document & doc) const;
    bool needFetchFromDocStore(const document::FieldSet &) const;
private:
    void populate(search::DocumentIdT lid, document::Document & doc, const document::Field::Set & attributeFields) const;

    bool isFieldAttribute(const document::Field & field) const override;
    const search::index::Schema     &_schema;
    const search::IAttributeManager &_attr_manager;
    const search::IDocumentStore    &_doc_store;
    PositionFields                   _possiblePositionFields;
    document::Field::Set             _attributeFields;
    bool                             _areAllFieldsAttributes;
    FieldSetAttributeDB              _fieldSetAttributeInfo;

    const search::IAttributeManager * getAttrMgr() const override;
};

}  // namespace proton

