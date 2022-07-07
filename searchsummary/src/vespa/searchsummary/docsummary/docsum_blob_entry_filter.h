// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "res_type.h"
#include <bitset>

namespace search::docsummary {

/*
 * Class containing the set of result types not stored in docsum blobs.
 * This is used for gradual migration towards elimination of docsum blobs.
 */
class DocsumBlobEntryFilter {
    std::bitset<14> _skip_types;

public:
    DocsumBlobEntryFilter()
        : _skip_types()
    {
    }
    bool skip(ResType type) const noexcept { return _skip_types.test(type); }
    DocsumBlobEntryFilter &add_skip(ResType type) {
        _skip_types.set(type);
        return *this;
    }
};

}
