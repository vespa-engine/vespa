// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentmetastorecontext.h"

namespace proton {

DocumentMetaStoreContext::ReadGuard::ReadGuard(const search::AttributeVector::SP &metaStoreAttr) :
    _guard(metaStoreAttr),
    _store(static_cast<const DocumentMetaStore &>(*_guard))
{
}


DocumentMetaStoreContext::DocumentMetaStoreContext(std::shared_ptr<bucketdb::BucketDBOwner> bucketDB,
                                                   const vespalib::string &name,
                                                   const search::GrowStrategy &grow) :
    _metaStoreAttr(std::make_shared<DocumentMetaStore>(std::move(bucketDB), name, grow)),
    _metaStore(std::dynamic_pointer_cast<IDocumentMetaStore>(_metaStoreAttr))
{
}


DocumentMetaStoreContext::DocumentMetaStoreContext(const search::AttributeVector::SP &metaStoreAttr) :
    _metaStoreAttr(metaStoreAttr),
    _metaStore(std::dynamic_pointer_cast<IDocumentMetaStore>(_metaStoreAttr))
{
}

DocumentMetaStoreContext::~DocumentMetaStoreContext() = default;

void
DocumentMetaStoreContext::constructFreeList()
{
    _metaStore->constructFreeList();
}

}
