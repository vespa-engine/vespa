// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_usage_sampler_context.h"
#include "attribute_usage_filter.h"
#include <vespa/searchcore/proton/common/transient_resource_usage_provider.h>

namespace proton {

AttributeUsageSamplerContext::AttributeUsageSamplerContext(AttributeUsageFilter& filter,
                                                           std::shared_ptr<const AttributeConfigInspector> attribute_config_inspector,
                                                           std::shared_ptr<TransientResourceUsageProvider> transient_usage_provider)
    : _usage(),
      _transient_memory_usage(0u),
      _lock(),
      _filter(filter),
      _attribute_config_inspector(std::move(attribute_config_inspector)),
      _transient_usage_provider(std::move(transient_usage_provider))
{
}

AttributeUsageSamplerContext::~AttributeUsageSamplerContext()
{
    _filter.setAttributeStats(_usage);
    _transient_usage_provider->set_transient_memory_usage(_transient_memory_usage);
}

void
AttributeUsageSamplerContext::merge(const search::AddressSpaceUsage &usage,
                                    size_t transient_memory_usage,
                                    const vespalib::string &attributeName,
                                    const vespalib::string &subDbName)
{
    Guard guard(_lock);
    _usage.merge(usage, attributeName, subDbName);
    _transient_memory_usage = std::max(_transient_memory_usage, transient_memory_usage);
}

} // namespace proton
