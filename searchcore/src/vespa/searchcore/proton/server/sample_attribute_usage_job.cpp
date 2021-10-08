// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sample_attribute_usage_job.h"
#include <vespa/searchcore/proton/attribute/i_attribute_manager.h>
#include <vespa/searchcore/proton/attribute/attribute_config_inspector.h>
#include <vespa/searchcore/proton/attribute/attribute_usage_filter.h>
#include <vespa/searchcore/proton/attribute/attribute_usage_sampler_context.h>
#include <vespa/searchcore/proton/attribute/attribute_usage_sampler_functor.h>

namespace proton {

SampleAttributeUsageJob::
SampleAttributeUsageJob(IAttributeManagerSP readyAttributeManager,
                        IAttributeManagerSP notReadyAttributeManager,
                        AttributeUsageFilter &attributeUsageFilter,
                        const vespalib::string &docTypeName,
                        vespalib::duration interval,
                        std::unique_ptr<const AttributeConfigInspector> attribute_config_inspector,
                        std::shared_ptr<TransientResourceUsageProvider> transient_usage_provider)
    : IMaintenanceJob("sample_attribute_usage." + docTypeName, vespalib::duration::zero(), interval),
      _readyAttributeManager(readyAttributeManager),
      _notReadyAttributeManager(notReadyAttributeManager),
      _attributeUsageFilter(attributeUsageFilter),
      _attribute_config_inspector(std::move(attribute_config_inspector)),
      _transient_usage_provider(std::move(transient_usage_provider))
{
}

SampleAttributeUsageJob::~SampleAttributeUsageJob() = default;

bool
SampleAttributeUsageJob::run()
{
    auto context = std::make_shared<AttributeUsageSamplerContext> (_attributeUsageFilter, _attribute_config_inspector, _transient_usage_provider);
    _readyAttributeManager->asyncForEachAttribute(std::make_shared<AttributeUsageSamplerFunctor>(context, "ready"));
    _notReadyAttributeManager->asyncForEachAttribute(std::make_shared<AttributeUsageSamplerFunctor>(context, "notready"));
    return true;
}

} // namespace proton
