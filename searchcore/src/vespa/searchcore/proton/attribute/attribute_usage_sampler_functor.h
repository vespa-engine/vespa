// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_usage_sampler_context.h"

#include <vespa/searchcommon/attribute/i_attribute_functor.h>

#include <memory>

namespace proton {

/**
 * Functor for sampling attribute usage and passing it on to sampler context.
 */
class AttributeUsageSamplerFunctor : public search::attribute::IConstAttributeFunctor {
    std::shared_ptr<AttributeUsageSamplerContext> _samplerContext;
    AttributeUsageSamplerContext::SubDb           _sub_db;
    std::string                                   _subDbName;

public:
    AttributeUsageSamplerFunctor(std::shared_ptr<AttributeUsageSamplerContext> samplerContext,
                                 AttributeUsageSamplerContext::SubDb sub_db, const std::string& subDbname);
    ~AttributeUsageSamplerFunctor() override;
    void operator()(const search::attribute::IAttributeVector& attributeVector) override;
};

} // namespace proton
