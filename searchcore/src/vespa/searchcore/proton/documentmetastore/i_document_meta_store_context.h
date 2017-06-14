// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_document_meta_store.h"

namespace proton {

/**
 * API for providing write and read interface to the document meta store.
 */
struct IDocumentMetaStoreContext {

    /**
     * Guard for access to the read interface.
     * This guard should be alive as long as read interface is used.
     */
    struct IReadGuard {

        typedef std::unique_ptr<IReadGuard> UP;

        virtual ~IReadGuard() {}

        /**
         * Access to read interface.
         */
        virtual const search::IDocumentMetaStore &get() const = 0;
    };

    typedef std::shared_ptr<IDocumentMetaStoreContext> SP;

    virtual ~IDocumentMetaStoreContext() {}

    /**
     * Access to write interface.
     * Should only be used by the writer thread.
     */
    virtual proton::IDocumentMetaStore & get() = 0;
    virtual proton::IDocumentMetaStore::SP getSP() const = 0;

    /**
     * Access to read interface.
     * Should be used by all reader threads.
     */
    virtual IReadGuard::UP getReadGuard() const = 0;

    /**
     * Construct free lists of underlying meta store.
     */
    virtual void constructFreeList() = 0;
};

} // namespace proton

