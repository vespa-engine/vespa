// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "loadedenumvalue.h"
#include <vespa/searchlib/common/sort.h>

namespace search {
namespace attribute {

void
sortLoadedByEnum(LoadedEnumAttributeVector &loaded)
{
    ShiftBasedRadixSorter<LoadedEnumAttribute,
        LoadedEnumAttribute::EnumRadix,
        LoadedEnumAttribute::EnumCompare, 56>::
        radix_sort(LoadedEnumAttribute::EnumRadix(),
                   LoadedEnumAttribute::EnumCompare(),
                   &loaded[0], loaded.size(), 16);
}

} // namespace attribute
} // namespace search

