// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "loadedenumvalue.h"
#include <vespa/searchlib/common/sort.h>

namespace search::attribute {

void
sortLoadedByEnum(LoadedEnumAttributeVector &loaded)
{
    ShiftBasedRadixSorter<LoadedEnumAttribute,
        LoadedEnumAttribute::EnumRadix,
        LoadedEnumAttribute::EnumCompare, 56>::
        radix_sort(LoadedEnumAttribute::EnumRadix(),
                   LoadedEnumAttribute::EnumCompare(),
                   loaded.data(), loaded.size(), 16);
}

}

