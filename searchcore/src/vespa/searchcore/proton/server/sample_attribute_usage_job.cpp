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
                        vespalib::duration interval)
    : IMaintenanceJob("sample_attribute_usage." + docTypeName, vespalib::duration::zero(), interval),
      _readyAttributeManager(std::move(readyAttributeManager)),
      _notReadyAttributeManager(std::move(notReadyAttributeManager)),
      _attributeUsageFilter(attributeUsageFilter)
{
}

SampleAttributeUsageJob::~SampleAttributeUsageJob() = default;

bool
SampleAttributeUsageJob::run()
{
    auto context = std::make_shared<AttributeUsageSamplerContext> (_attributeUsageFilter);
    _readyAttributeManager->asyncForEachAttribute(std::make_shared<AttributeUsageSamplerFunctor>(context, "ready"));
    _notReadyAttributeManager->asyncForEachAttribute(std::make_shared<AttributeUsageSamplerFunctor>(context, "notready"));
    return true;
}

} // namespace proton
