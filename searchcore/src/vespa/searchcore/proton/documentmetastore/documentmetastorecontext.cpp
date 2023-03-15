// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentmetastorecontext.h"
#include "documentmetastore.h"

namespace proton {

DocumentMetaStoreContext::ReadGuard::ReadGuard(const std::shared_ptr<search::AttributeVector> & metaStoreAttr) :
    _guard(metaStoreAttr),
    _store(static_cast<const DocumentMetaStore &>(*_guard))
{
}

const search::IDocumentMetaStore &
DocumentMetaStoreContext::ReadGuard::get() const {
    return _store;
}

DocumentMetaStoreContext::DocumentMetaStoreContext(std::shared_ptr<bucketdb::BucketDBOwner> bucketDB)
    : DocumentMetaStoreContext(std::move(bucketDB), DocumentMetaStore::getFixedName(), search::GrowStrategy())
{}

DocumentMetaStoreContext::DocumentMetaStoreContext(std::shared_ptr<bucketdb::BucketDBOwner> bucketDB,
                                                   const vespalib::string &name,
                                                   const search::GrowStrategy &grow)
    : _metaStoreAttr(std::make_shared<DocumentMetaStore>(std::move(bucketDB), name, grow)),
      _metaStore(std::dynamic_pointer_cast<IDocumentMetaStore>(_metaStoreAttr))
{
}


DocumentMetaStoreContext::DocumentMetaStoreContext(std::shared_ptr<search::AttributeVector> metaStoreAttr) :
    _metaStoreAttr(std::move(metaStoreAttr)),
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
