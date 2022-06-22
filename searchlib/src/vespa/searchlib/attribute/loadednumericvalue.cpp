// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "loadednumericvalue.h"
#include <vespa/searchlib/common/sort.h>

namespace search::attribute {

template <typename T>
void
sortLoadedByValue(SequentialReadModifyWriteVector<LoadedNumericValue<T>> & loaded)
{
    ShiftBasedRadixSorter<LoadedNumericValue<T>,
        typename LoadedNumericValue<T>::ValueRadix,
        typename LoadedNumericValue<T>::ValueCompare, 56>::
        radix_sort(typename LoadedNumericValue<T>::ValueRadix(),
                   typename LoadedNumericValue<T>::ValueCompare(),
                   loaded.data(),
                   loaded.size(),
                   16);
}


template <typename T>
void
sortLoadedByDocId(SequentialReadModifyWriteVector<LoadedNumericValue<T>> & loaded)
{
    ShiftBasedRadixSorter<LoadedNumericValue<T>,
        typename LoadedNumericValue<T>::DocRadix,
        typename LoadedNumericValue<T>::DocOrderCompare, 56>::
        radix_sort(typename LoadedNumericValue<T>::DocRadix(),
                   typename LoadedNumericValue<T>::DocOrderCompare(),
                   loaded.data(),
                   loaded.size(),
                   16);
}


template
void 
sortLoadedByValue(SequentialReadModifyWriteVector<LoadedNumericValue<int8_t>> & loaded);

template
void 
sortLoadedByValue(SequentialReadModifyWriteVector<LoadedNumericValue<int16_t>> & loaded);

template
void 
sortLoadedByValue(SequentialReadModifyWriteVector<LoadedNumericValue<int32_t>> & loaded);

template
void 
sortLoadedByValue(SequentialReadModifyWriteVector<LoadedNumericValue<int64_t>> & loaded);

template
void 
sortLoadedByValue(SequentialReadModifyWriteVector<LoadedNumericValue<float>> & loaded);

template
void 
sortLoadedByValue(SequentialReadModifyWriteVector<LoadedNumericValue<double>> & loaded);
                  
template
void 
sortLoadedByDocId(SequentialReadModifyWriteVector<LoadedNumericValue<int8_t>> & loaded);

template
void 
sortLoadedByDocId(SequentialReadModifyWriteVector<LoadedNumericValue<int16_t>> & loaded);

template
void 
sortLoadedByDocId(SequentialReadModifyWriteVector<LoadedNumericValue<int32_t>> & loaded);

template
void 
sortLoadedByDocId(SequentialReadModifyWriteVector<LoadedNumericValue<int64_t>> & loaded);

template
void 
sortLoadedByDocId(SequentialReadModifyWriteVector<LoadedNumericValue<float>> & loaded);

template
void 
sortLoadedByDocId(SequentialReadModifyWriteVector<LoadedNumericValue<double>> & loaded);

}
