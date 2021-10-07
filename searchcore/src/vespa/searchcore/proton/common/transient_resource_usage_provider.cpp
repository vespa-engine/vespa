// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "transient_resource_usage_provider.h"

namespace proton {

TransientResourceUsageProvider::TransientResourceUsageProvider()
    : ITransientResourceUsageProvider(),
      _transient_memory_usage(0u)
{
}

TransientResourceUsageProvider::~TransientResourceUsageProvider() = default;

size_t
TransientResourceUsageProvider::get_transient_memory_usage() const
{
    return _transient_memory_usage.load(std::memory_order_relaxed);
}

void
TransientResourceUsageProvider::set_transient_memory_usage(size_t transient_memory_usage)
{
    _transient_memory_usage.store(transient_memory_usage, std::memory_order_relaxed);
}

}
