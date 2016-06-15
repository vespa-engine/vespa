// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/common/serialnum.h>
#include <vespa/vespalib/stllike/string.h>

namespace proton {

/**
 * Interface for a factory for creating and setting up attribute vectors used by
 * an attribute manager.
 */
struct IAttributeFactory
{
    typedef std::shared_ptr<IAttributeFactory> SP;
    virtual ~IAttributeFactory() {}
    virtual search::AttributeVector::SP create(const vespalib::string &name,
                                               const search::attribute::Config &cfg) const = 0;
    virtual void setupEmpty(const search::AttributeVector::SP &vec,
                            search::SerialNum serialNum) const = 0;
};

} // namespace proton

