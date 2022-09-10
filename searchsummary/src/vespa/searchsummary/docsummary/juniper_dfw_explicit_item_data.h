// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace search::docsummary {

/*
 * Explicit values used when JuniperDFWQueryItem doesn't have a query
 * stack dump iterator.
 */
struct JuniperDFWExplicitItemData
{
    vespalib::stringref _index;
    int32_t _weight;

    JuniperDFWExplicitItemData()
        : _index(), _weight(0)
    {}
};

}
