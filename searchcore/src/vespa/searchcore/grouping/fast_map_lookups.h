// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcommon/common/schema.h>

#include <string_view>
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

    /**
     * Returns the lookup whose hidden attribute is named "fieldname$lookupname", or nullptr if there is none.
     */
    [[nodiscard]] const FastMapLookup* find(std::string_view lookup_attribute) const noexcept;
    bool operator==(const FastMapLookups& rhs) const noexcept = default;
};

} // namespace search::grouping
