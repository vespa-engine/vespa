// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_attribute_factory.h"

namespace proton {

/**
 * Concrete factory for creating attribute vectors by using the search::AttributeFactory.
 */
class AttributeFactory : public IAttributeFactory
{
public:
    typedef std::shared_ptr<AttributeFactory> SP;
    AttributeFactory();

    AttributeVectorSP create(const vespalib::string &name, const search::attribute::Config &cfg) const override;
    void setupEmpty(const AttributeVectorSP &vec, search::SerialNum serialNum) const override;
};

}
