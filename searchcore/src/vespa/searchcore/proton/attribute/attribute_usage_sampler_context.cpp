// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_usage_sampler_context.h"
#include "attribute_usage_filter.h"

namespace proton {

AttributeUsageSamplerContext::AttributeUsageSamplerContext(AttributeUsageFilter& filter)
    : _usage(),
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
                                    const vespalib::string &attributeName,
                                    const vespalib::string &subDbName)
{
    Guard guard(_lock);
    _usage.merge(usage, attributeName, subDbName);
}

}
