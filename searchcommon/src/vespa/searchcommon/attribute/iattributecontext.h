// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "iattributevector.h"
#include <vector>
#include <memory>

namespace search {
namespace attribute {

/**
 * This is an interface used to access all registered attribute vectors.
 **/
class IAttributeContext {
public:
    typedef vespalib::string string;
    /** Convenience typedefs **/
    typedef std::unique_ptr<IAttributeContext> UP;

    /**
     * Returns the attribute vector with the given name.
     *
     * @param name the name of the attribute vector.
     * @return const view of the attribute vector or NULL if the attribute vector does not exists.
     **/
    virtual const IAttributeVector * getAttribute(const string & name) const = 0;

    /**
     * Returns the attribute vector with the given name.
     * Makes sure that the underlying enum values are stable during the use of this attribute.
     *
     * @param name the name of the attribute vector
     * @return const view of the attribute vector or NULL if the attribute vector does not exists.
     **/
    virtual const IAttributeVector * getAttributeStableEnum(const string & name) const = 0;

    /**
     * Fill the given list with all attribute vectors registered.
     *
     * @param list the list to fill in attribute vectors.
     **/
    virtual void getAttributeList(std::vector<const IAttributeVector *> & list) const = 0;

    /**
     * Releases all cached attribute guards.
     **/
    virtual void releaseEnumGuards() {}

    /**
     * Virtual destructor to allow safe subclassing.
     **/
    virtual ~IAttributeContext() {}
};

} // namespace attribute
} // namespace search

