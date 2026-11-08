// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fast_map_lookups.h"

namespace search::grouping {

FastMapLookups::FastMapLookups() noexcept = default;

FastMapLookups::FastMapLookups(std::vector<FastMapLookup> lookups) noexcept : _lookups(std::move(lookups)) {}

FastMapLookups::~FastMapLookups() = default;

} // namespace search::grouping
