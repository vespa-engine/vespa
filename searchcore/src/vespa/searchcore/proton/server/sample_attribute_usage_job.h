// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_maintenance_job.h"

namespace proton
{

class IAttributeManager;
class AttributeUsageFilter;

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
public:
    SampleAttributeUsageJob(IAttributeManagerSP readyAttributeManager,
                            IAttributeManagerSP notReadyAttributeManager,
                            AttributeUsageFilter &attributeUsageFilter,
                            const vespalib::string &docTypeName,
                            double interval);
    ~SampleAttributeUsageJob();

    virtual bool run() override;
};

} // namespace proton
