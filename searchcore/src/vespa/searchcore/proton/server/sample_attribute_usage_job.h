// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_maintenance_job.h"

namespace searchcorespi { class IIndexManager; }

namespace proton {

struct IAttributeManager;
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
    const std::string     _document_type;
    std::shared_ptr<searchcorespi::IIndexManager> _index_manager;

public:
    SampleAttributeUsageJob(IAttributeManagerSP readyAttributeManager,
                            IAttributeManagerSP notReadyAttributeManager,
                            AttributeUsageFilter &attributeUsageFilter,
                            const std::string &docTypeName,
                            vespalib::duration interval,
                            std::shared_ptr<searchcorespi::IIndexManager> index_manager);
    ~SampleAttributeUsageJob() override;

    bool run() override;
    void onStop() override { }
};

} // namespace proton
