// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_transient_resource_usage_provider.h"

#include <atomic>

namespace proton {

/**
 * Class providing transient resource usage.
 * E.g. extra memory needed for loading or saving an attribute vector.
 * It provides an aggregated view over several components (e.g. all attribute vectors for a document type).
 */
class TransientResourceUsageProvider : public ITransientResourceUsageProvider {
    std::atomic<size_t> _transient_memory_usage;
public:
    TransientResourceUsageProvider();
    virtual ~TransientResourceUsageProvider();
    size_t get_transient_memory_usage() const override;
    size_t get_transient_disk_usage() const override { return 0; }
    void set_transient_memory_usage(size_t transient_memory_usage);
};

}
