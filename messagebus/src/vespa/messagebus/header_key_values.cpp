// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "header_key_values.h"
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace mbus {

HeaderKeyValues::HeaderKeyValues() = default;

HeaderKeyValues::HeaderKeyValues(Map&& map) noexcept
    : _map(std::move(map))
{
}

HeaderKeyValues::~HeaderKeyValues() = default;

}
