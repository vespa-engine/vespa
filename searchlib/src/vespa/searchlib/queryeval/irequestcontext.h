// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/doom.h>
#include <vespa/searchlib/attribute/attributevector.h>

namespace search {
namespace queryeval {

/**
 * Provides a context that follows the life of a query.
 */
class IRequestContext
{
public:
    virtual ~IRequestContext() { }
    /**
     * Provides the time of doom for the query.
     * @return time of doom.
     */
    virtual const vespalib::Doom & getDoom() const = 0;

    /**
     * Provides the time of soft doom for the query. Now it is time to start cleaning up and return what you have.
     * @return time of soft doom.
     */
    virtual const vespalib::Doom & getSoftDoom() const = 0;

    /**
     * Provide access to attributevectors
     * @return AttributeVector or nullptr if it does not exist.
     */
    virtual const AttributeVector * getAttribute(const vespalib::string & name) const = 0;
    virtual const AttributeVector * getAttributeStableEnum(const vespalib::string & name) const = 0;
};

} // namespace queryeval
} // namespace search
