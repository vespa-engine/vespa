// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <cstdint>

namespace search { class AttributeVector; }

namespace search::attribute {

class AttributeHeader;
class Config;

}

namespace proton {

/**
 * Class to calculate transient memory during load of attribute vector
 * in the future based on current attribute vector and new config.
 */
class AttributeTransientMemoryCalculator
{
public:
    AttributeTransientMemoryCalculator() = default;
    ~AttributeTransientMemoryCalculator() = default;
    size_t operator()(const search::AttributeVector& attribute_vector,
                      const search::attribute::Config& new_config) const;
    size_t operator()(const search::attribute::AttributeHeader& old_header,
                      const search::attribute::Config& new_config) const;
};

}
