// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/doom.h>
#include <vespa/vespalib/stllike/string.h>

namespace search::attribute { class IAttributeVector; }

namespace search::queryeval {

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

}
