// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcommon/common/schema.h>
#include <vespa/vespalib/stllike/hash_map.h>

#include <string>
#include <vector>

namespace search::expression {

using FastMapLookup = search::index::Schema::FastMapLookup;

/**
 * The fast map lookups of a document type, used to configure DecodeFastMapLookupNode in grouping.
 **/
class FastMapLookups {
private:
    vespalib::hash_map<std::string, FastMapLookup> _lookups;

public:
    FastMapLookups();
    explicit FastMapLookups(const std::vector<FastMapLookup>& lookups);
    ~FastMapLookups();

    /**
     * Returns the lookup named "fieldname.lookupname", or nullptr if there is none.
     */
    [[nodiscard]] const FastMapLookup* find(const std::string& name) const noexcept;
};

} // namespace search::expression
