// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "loadedstringvalue.h"

using vespalib::Array;
using vespalib::alloc::Alloc;

namespace search {
namespace attribute {

void
sortLoadedByValue(LoadedStringVectorReal &loaded)
{
    Array<unsigned> radixScratchPad(loaded.size(), Alloc::allocMMap());
    for(size_t i(0), m(loaded.size()); i < m; i++) {
        loaded[i].prepareRadixSort();
    }
    radix_sort(LoadedStringValue::ValueRadix(),
               LoadedStringValue::ValueCompare(),
               AlwaysEof<LoadedStringValue>(),
               1,
               &loaded[0],
               loaded.size(),
               &radixScratchPad[0],
               0,
               96);
}

void
sortLoadedByDocId(LoadedStringVectorReal &loaded)
{
    ShiftBasedRadixSorter<LoadedStringValue,
        LoadedStringValue::DocRadix,
        LoadedStringValue::DocOrderCompare, 56>::
        radix_sort(LoadedStringValue::DocRadix(),
                   LoadedStringValue::DocOrderCompare(),
                   &loaded[0],
                   loaded.size(),
                   16);
}


} // namespace attribute
} // namespace search

