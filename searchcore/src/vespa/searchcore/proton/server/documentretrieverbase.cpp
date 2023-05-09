// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentretrieverbase.h"
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/vespalib/stllike/lrucache_map.hpp>
#include <vespa/vespalib/util/stringfmt.h>

using document::DocumentId;
using document::GlobalId;

namespace proton {

DocumentRetrieverBase::DocumentRetrieverBase(const DocTypeName &docTypeName, const document::DocumentTypeRepo &repo,
                                             const IDocumentMetaStoreContext &meta_store, bool hasFields)
    : IDocumentRetriever(),
      _docTypeName(docTypeName),
      _repo(repo),
      _meta_store(meta_store),
      _selectCache(256u),
      _lock(),
      _emptyDoc(),
      _hasFields(hasFields)
{
    const document::DocumentType * docType(_repo.getDocumentType(_docTypeName.getName()));
    _emptyDoc = std::make_unique<document::Document>(_repo, *docType, DocumentId("id:empty:" + _docTypeName.getName() + "::empty"));
}

DocumentRetrieverBase::~DocumentRetrieverBase() = default;

void
DocumentRetrieverBase::getBucketMetaData(const storage::spi::Bucket &bucket,
                                         search::DocumentMetaData::Vector &result) const
{
    _meta_store.getReadGuard()->get().getMetaData(bucket, result);
}

search::DocumentMetaData
DocumentRetrieverBase::getDocumentMetaData(const DocumentId &id) const {
    return _meta_store.getReadGuard()->get().getMetaData(id.getGlobalId());
}
    
CachedSelect::SP
DocumentRetrieverBase::parseSelect(const vespalib::string &selection) const
{
    {
        std::lock_guard<std::mutex> guard(_lock);
        if (_selectCache.hasKey(selection))
            return _selectCache[selection];
    }
    
    auto nselect = std::make_shared<CachedSelect>();
    
    nselect->set(selection, _docTypeName.getName(), *_emptyDoc, getDocumentTypeRepo(), getAttrMgr(), _hasFields);

    std::lock_guard<std::mutex> guard(_lock);
    if (_selectCache.hasKey(selection))
        return _selectCache[selection];
    _selectCache.insert(selection, nselect);
    return nselect;
}

}  // namespace proton
