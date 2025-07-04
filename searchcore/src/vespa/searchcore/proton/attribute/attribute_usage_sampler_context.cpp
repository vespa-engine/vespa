// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_usage_sampler_context.h"
#include "attribute_usage_filter.h"

namespace proton {

AttributeUsageSamplerContext::AttributeUsageSamplerContext(const std::string& document_type, AttributeUsageFilter& filter)
    : _usage(document_type),
      _lock(),
      _filter(filter)
{
}

AttributeUsageSamplerContext::~AttributeUsageSamplerContext()
{
    _filter.setAttributeStats(_usage);
}

void
AttributeUsageSamplerContext::merge(const search::AddressSpaceUsage &usage,
                                    const std::string &attributeName,
                                    const std::string &subDbName)
{
    Guard guard(_lock);
    _usage.merge(usage, attributeName, subDbName);
}

}
