// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/persistenceengine/i_document_retriever.h>
#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/stllike/lrucache_map.h>
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <mutex>

namespace proton {

class DocumentRetrieverBase : public IDocumentRetriever
{
    const DocTypeName                &_docTypeName;
    const document::DocumentTypeRepo &_repo;
    const IDocumentMetaStoreContext  &_meta_store;

    using SelectCache = vespalib::lrucache_map<vespalib::LruParam<vespalib::string, CachedSelect::SP>>;

    mutable SelectCache    _selectCache;
    mutable std::mutex     _lock;
    document::Document::UP _emptyDoc;
    const bool             _hasFields;

protected:
    virtual const search::IAttributeManager * getAttrMgr() const { return nullptr; }
    const document::DocumentType & getDocumentType() const {
        return _emptyDoc->getType();
    }
public:
    DocumentRetrieverBase(const DocTypeName &docTypeName,
                          const document::DocumentTypeRepo &repo,
                          const IDocumentMetaStoreContext &meta_store,
                          bool hasFields);
    ~DocumentRetrieverBase() override;

    const document::DocumentTypeRepo &getDocumentTypeRepo() const override { return _repo; }
    void getBucketMetaData(const storage::spi::Bucket &bucket, search::DocumentMetaData::Vector &result) const override;
    search::DocumentMetaData getDocumentMetaData(const document::DocumentId &id) const override;
    CachedSelect::SP parseSelect(const vespalib::string &selection) const override;
    ReadGuard getReadGuard() const override { return _meta_store.getReadGuard(); }
    uint32_t getDocIdLimit() const override { return _meta_store.getReadGuard()->get().getCommittedDocIdLimit(); }
};

}  // namespace proton

