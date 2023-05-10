// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_document_meta_store.h"
#include <vespa/searchcommon/attribute/i_document_meta_store_context.h>

namespace proton {

/**
 * API for providing write and read interface to the document meta store.
 */
struct IDocumentMetaStoreContext : public search::IDocumentMetaStoreContext {

    using SP = std::shared_ptr<IDocumentMetaStoreContext>;

    virtual ~IDocumentMetaStoreContext() = default;

    /**
     * Access to write interface.
     * Should only be used by the writer thread.
     */
    virtual proton::IDocumentMetaStore &get() = 0;
    virtual proton::IDocumentMetaStore::SP getSP() const = 0;

    /**
     * Construct free lists of underlying meta store.
     */
    virtual void constructFreeList() = 0;
};

} // namespace proton

