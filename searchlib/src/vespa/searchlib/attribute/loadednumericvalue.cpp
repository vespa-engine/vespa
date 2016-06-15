// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "loadednumericvalue.h"


namespace search
{

namespace attribute
{

template <typename T>
void
sortLoadedByValue(SequentialReadModifyWriteVector<LoadedNumericValue<T>,
                                                  vespalib::DefaultAlloc> &
                  loaded)
{
    ShiftBasedRadixSorter<LoadedNumericValue<T>,
        typename LoadedNumericValue<T>::ValueRadix,
        typename LoadedNumericValue<T>::ValueCompare, 56>::
        radix_sort(typename LoadedNumericValue<T>::ValueRadix(),
                   typename LoadedNumericValue<T>::ValueCompare(),
                   &loaded[0],
                   loaded.size(),
                   16);
}


template <typename T>
void
sortLoadedByDocId(SequentialReadModifyWriteVector<LoadedNumericValue<T>,
                                                  vespalib::DefaultAlloc> &
                  loaded)
{
    ShiftBasedRadixSorter<LoadedNumericValue<T>,
        typename LoadedNumericValue<T>::DocRadix,
        typename LoadedNumericValue<T>::DocOrderCompare, 56>::
        radix_sort(typename LoadedNumericValue<T>::DocRadix(),
                   typename LoadedNumericValue<T>::DocOrderCompare(),
                   &loaded[0],
                   loaded.size(),
                   16);
}


template
void 
sortLoadedByValue(SequentialReadModifyWriteVector<LoadedNumericValue<int8_t>,
                                                  vespalib::DefaultAlloc> &
                                                  loaded);

template
void 
sortLoadedByValue(SequentialReadModifyWriteVector<LoadedNumericValue<int16_t>,
                                                  vespalib::DefaultAlloc> &
                                                  loaded);

template
void 
sortLoadedByValue(SequentialReadModifyWriteVector<LoadedNumericValue<int32_t>,
                                                  vespalib::DefaultAlloc> &
                                                  loaded);

template
void 
sortLoadedByValue(SequentialReadModifyWriteVector<LoadedNumericValue<int64_t>,
                                                  vespalib::DefaultAlloc> &
                                                  loaded);

template
void 
sortLoadedByValue(SequentialReadModifyWriteVector<LoadedNumericValue<float>,
                                                  vespalib::DefaultAlloc> &
                                                  loaded);

template
void 
sortLoadedByValue(SequentialReadModifyWriteVector<LoadedNumericValue<double>,
                                                  vespalib::DefaultAlloc> &
                  loaded);
                  
template
void 
sortLoadedByDocId(SequentialReadModifyWriteVector<LoadedNumericValue<int8_t>,
                                                  vespalib::DefaultAlloc> &
                                                  loaded);

template
void 
sortLoadedByDocId(SequentialReadModifyWriteVector<LoadedNumericValue<int16_t>,
                                                  vespalib::DefaultAlloc> &
                                                  loaded);

template
void 
sortLoadedByDocId(SequentialReadModifyWriteVector<LoadedNumericValue<int32_t>,
                                                  vespalib::DefaultAlloc> &
                                                  loaded);

template
void 
sortLoadedByDocId(SequentialReadModifyWriteVector<LoadedNumericValue<int64_t>,
                                                  vespalib::DefaultAlloc> &
                                                  loaded);

template
void 
sortLoadedByDocId(SequentialReadModifyWriteVector<LoadedNumericValue<float>,
                                                  vespalib::DefaultAlloc> &
                                                  loaded);

template
void 
sortLoadedByDocId(SequentialReadModifyWriteVector<LoadedNumericValue<double>,
                                                  vespalib::DefaultAlloc> &
                  loaded);
                  


} // namespace attribute

} // namespace search

