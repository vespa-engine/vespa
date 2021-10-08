// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_maintenance_job.h"

namespace proton {

struct IAttributeManager;
class AttributeConfigInspector;
class AttributeUsageFilter;
class TransientResourceUsageProvider;

/**
 * Class used to sample attribute resource usage and pass aggregated
 * information to attribute usage filter to block feeding before
 * proton crashes due to attribute structure size limitations.
 */
class SampleAttributeUsageJob : public IMaintenanceJob
{
    using IAttributeManagerSP = std::shared_ptr<IAttributeManager>;

    IAttributeManagerSP   _readyAttributeManager;
    IAttributeManagerSP   _notReadyAttributeManager;
    AttributeUsageFilter &_attributeUsageFilter;
    std::shared_ptr<const AttributeConfigInspector> _attribute_config_inspector;
    std::shared_ptr<TransientResourceUsageProvider> _transient_usage_provider;
public:
    SampleAttributeUsageJob(IAttributeManagerSP readyAttributeManager,
                            IAttributeManagerSP notReadyAttributeManager,
                            AttributeUsageFilter &attributeUsageFilter,
                            const vespalib::string &docTypeName,
                            vespalib::duration interval,
                            std::unique_ptr<const AttributeConfigInspector> attribute_config_inspector,
                            std::shared_ptr<TransientResourceUsageProvider> transient_usage_provider);
    ~SampleAttributeUsageJob() override;

    bool run() override;
    void onStop() override { }
};

} // namespace proton
