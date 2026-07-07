// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/initializer/load_memory_usage.h>

#include <cstddef>
#include <cstdint>

namespace search {
class AttributeVector;
}

namespace search::attribute {

class AttributeHeader;
class Config;

} // namespace search::attribute

namespace proton {

/**
 * Class to calculate transient and permanent memory during load of attribute vector
 * in the future based on current attribute vector and new config.
 */
class AttributeLoadMemoryCalculator {
public:
    AttributeLoadMemoryCalculator() = default;
    ~AttributeLoadMemoryCalculator() = default;
    initializer::LoadMemoryUsage operator()(const search::AttributeVector&   attribute_vector,
                                            const search::attribute::Config& new_config) const;
    initializer::LoadMemoryUsage operator()(const search::attribute::AttributeHeader& old_header,
                                            const search::attribute::Config&          new_config) const;
};

} // namespace proton
