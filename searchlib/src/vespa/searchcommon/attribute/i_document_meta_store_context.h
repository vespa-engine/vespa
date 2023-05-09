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

        using SP = std::shared_ptr<IReadGuard>;

        virtual ~IReadGuard() = default;

        /**
         * Access to read interface.
         */
        virtual const search::IDocumentMetaStore &get() const = 0;
    };

    virtual ~IDocumentMetaStoreContext() = default;

    /**
     * Access to read interface.
     * Should be used by all reader threads.
     */
    virtual IReadGuard::SP getReadGuard() const = 0;

};

}
