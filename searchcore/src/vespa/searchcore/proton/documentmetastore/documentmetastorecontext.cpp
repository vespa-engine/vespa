// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentmetastorecontext.h"

namespace proton {

DocumentMetaStoreContext::ReadGuard::ReadGuard(const search::AttributeVector::SP &metaStoreAttr) :
    _guard(metaStoreAttr),
    _store(static_cast<const DocumentMetaStore &>(*_guard))
{
}


DocumentMetaStoreContext::DocumentMetaStoreContext(BucketDBOwner::SP bucketDB,
                                                   const vespalib::string &name,
                                                   const search::GrowStrategy &grow,
                                                   const DocumentMetaStore::IGidCompare::SP &gidCompare) :
    _metaStoreAttr(new DocumentMetaStore(bucketDB, name, grow, gidCompare)),
    _metaStore(std::dynamic_pointer_cast<IDocumentMetaStore>(_metaStoreAttr))
{
}


DocumentMetaStoreContext::DocumentMetaStoreContext(const search::AttributeVector::SP &metaStoreAttr) :
    _metaStoreAttr(metaStoreAttr),
    _metaStore(std::dynamic_pointer_cast<IDocumentMetaStore>(_metaStoreAttr))
{
}

DocumentMetaStoreContext::~DocumentMetaStoreContext() {}

void
DocumentMetaStoreContext::constructFreeList()
{
    _metaStore->constructFreeList();
}

}
