// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/doom.h>
#include <vespa/vespalib/stllike/string.h>

namespace search {

namespace attribute {
class IAttributeVector;
}

namespace queryeval {

/**
 * Provides a context that follows the life of a query.
 */
class IRequestContext
{
public:
    virtual ~IRequestContext() { }

    /**
     * Provides the time of soft doom for the query. Now it is time to start cleaning up and return what you have.
     * @return time of soft doom.
     */
    virtual const vespalib::Doom & getSoftDoom() const = 0;

    /**
     * Provide access to attributevectors
     * @return AttributeVector or nullptr if it does not exist.
     */
    virtual const attribute::IAttributeVector *getAttribute(const vespalib::string &name) const = 0;
    virtual const attribute::IAttributeVector *getAttributeStableEnum(const vespalib::string &name) const = 0;
};

} // namespace queryeval
} // namespace search
