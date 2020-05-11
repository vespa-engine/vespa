// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_transient_memory_usage_provider.h"

#include <atomic>

namespace proton {

/*
 * Class providing transient memory usage, e.g. extra memory needed
 * for loading or saving an attribute vector.
 */
class TransientMemoryUsageProvider : public ITransientMemoryUsageProvider {
    std::atomic<size_t> _transient_memory_usage;
public:
    TransientMemoryUsageProvider();
    virtual ~TransientMemoryUsageProvider();
    size_t get_transient_memory_usage() const override;
    void set_transient_memory_usage(size_t transient_memory_usage);
};

}
