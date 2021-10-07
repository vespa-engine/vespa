// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace search {

struct IDocumentMetaStore;

/**
 * API for providing read interface to the document meta store.
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

    virtual ~IDocumentMetaStoreContext() {}

    /**
     * Access to read interface.
     * Should be used by all reader threads.
     */
    virtual IReadGuard::UP getReadGuard() const = 0;

};

}
