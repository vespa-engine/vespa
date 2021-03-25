// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>

namespace proton {

/**
 * Interface class providing transient resource usage.
 * E.g. extra memory needed for loading or saving an attribute vector.
 * It provides an aggregated view over several components (e.g. all attribute vectors for a document type).
 */
class ITransientResourceUsageProvider {
public:
    virtual ~ITransientResourceUsageProvider() = default;
    virtual size_t get_transient_memory_usage() const = 0;
};

}
