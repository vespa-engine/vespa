// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_attribute_functor.h"
#include "iattributevector.h"

namespace search::attribute {

/**
 * This is an interface used to access all registered attribute vectors.
 **/
class IAttributeContext : public IAttributeExecutor {
public:
    using string = std::string;
    /** Convenience typedefs **/
    using UP = std::unique_ptr<IAttributeContext>;

    /**
     * Returns the attribute vector with the given name.
     *
     * @param name the name of the attribute vector.
     * @return const view of the attribute vector or NULL if the attribute vector does not exists.
     **/
    virtual const IAttributeVector* getAttribute(std::string_view name) const = 0;

    /**
     * Returns the attribute vector with the given name.
     * Makes sure that the underlying enum values are stable during the use of this attribute.
     * The stable enum guard is cached in the context and held until releaseEnumGuards() is
     * called or the context is destroyed. The guard itself is not bound to a thread: it may be
     * taken by one thread and released by another. Calling this concurrently from several
     * threads still requires enableMultiThreadSafe() to have been called on the context.
     *
     * @param name the name of the attribute vector
     * @return const view of the attribute vector or NULL if the attribute vector does not exists.
     **/
    virtual const IAttributeVector* getAttributeStableEnum(std::string_view name) const = 0;

    /**
     * Fill the given list with all attribute vectors registered.
     *
     * @param list the list to fill in attribute vectors.
     **/
    virtual void getAttributeList(std::vector<const IAttributeVector*>& list) const = 0;

    /**
     * Releases all cached stable enum guards (see getAttributeStableEnum()).
     **/
    virtual void releaseEnumGuards() {}

    /**
     * Must be called before multiple threads will access the context.
     */
    virtual void enableMultiThreadSafe() {}
};

} // namespace search::attribute
