// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "document_meta_store_observer.h"
#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store_context.h>

namespace proton::test {

struct DocumentMetaStoreContextObserver : public IDocumentMetaStoreContext
{
    using SP = std::shared_ptr<DocumentMetaStoreContextObserver>;

    IDocumentMetaStoreContext &_context;
    DocumentMetaStoreObserver::SP _observer;

    DocumentMetaStoreContextObserver(IDocumentMetaStoreContext &context)
        : _context(context),
          _observer(std::make_shared<DocumentMetaStoreObserver>(_context.get()))
    {
    }
    const DocumentMetaStoreObserver &getObserver() const {
        return * dynamic_cast<const DocumentMetaStoreObserver *>(_observer.get());
    }

    // Implements IDocumentMetaStoreContext
    proton::IDocumentMetaStore::SP getSP() const override { return _observer; }
    proton::IDocumentMetaStore &     get()       override { return *_observer; }
    IReadGuard::SP          getReadGuard() const override { return _context.getReadGuard(); }
    void               constructFreeList()       override { return _context.constructFreeList(); }
};

}

