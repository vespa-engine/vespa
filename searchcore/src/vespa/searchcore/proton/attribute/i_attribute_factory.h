// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/serialnum.h>
#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace search { class AttributeVector; }
namespace search::attribute { class Config; }

namespace proton {

/**
 * Interface for a factory for creating and setting up attribute vectors used by
 * an attribute manager.
 */
struct IAttributeFactory
{
    typedef std::shared_ptr<IAttributeFactory> SP;
    using AttributeVectorSP = std::shared_ptr<search::AttributeVector>;
    virtual ~IAttributeFactory() {}
    virtual AttributeVectorSP create(const vespalib::string &name,
                                     const search::attribute::Config &cfg) const = 0;
    virtual void setupEmpty(const AttributeVectorSP &vec,
                            search::SerialNum serialNum) const = 0;
};

} // namespace proton

