// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_attribute_functor.h"
#include <memory>

namespace proton {

class AttributeUsageSamplerContext;

/*
 * Functor for sampling attribute usage and passing it on to sampler
 * context.
 */
class AttributeUsageSamplerFunctor : public IAttributeFunctor
{
    std::shared_ptr<AttributeUsageSamplerContext> _samplerContext;
    std::string _subDbName;
public:
    AttributeUsageSamplerFunctor(std::shared_ptr<AttributeUsageSamplerContext>
                                 samplerContext,
                                 const std::string &subDbname);
    ~AttributeUsageSamplerFunctor();
    virtual void
    operator()(const search::AttributeVector &attributeVector) override;
};

} // namespace proton
