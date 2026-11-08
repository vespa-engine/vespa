// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcommon/common/schema.h>

#include <vector>

namespace search::grouping {

using FastMapLookup = search::index::Schema::FastMapLookup;

/**
 * The fast map lookups of a document type, available to grouping.
 **/
class FastMapLookups {
private:
    std::vector<FastMapLookup> _lookups;

public:
    FastMapLookups() noexcept;
    explicit FastMapLookups(std::vector<FastMapLookup> lookups) noexcept;
    ~FastMapLookups();
    [[nodiscard]] const std::vector<FastMapLookup>& lookups() const noexcept { return _lookups; }
    [[nodiscard]] bool empty() const noexcept { return _lookups.empty(); }
    bool operator==(const FastMapLookups& rhs) const noexcept = default;
};

} // namespace search::grouping
