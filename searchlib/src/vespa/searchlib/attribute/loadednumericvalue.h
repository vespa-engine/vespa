// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "loadedvalue.h"
#include <vespa/vespalib/util/sort.h>
#include <vespa/searchlib/util/fileutil.h>

namespace search::attribute {

/**
 * Temporary representation of enumerated attribute loaded from non-enumerated
 * save file (i.e. old save format).  For numeric data types.
 */

template <typename T>
struct LoadedNumericValue : public LoadedValue<T>
{
    LoadedNumericValue() : LoadedValue<T>() { }

    class ValueCompare
    {
    public:
        bool operator()(const LoadedNumericValue<T> &x, const LoadedNumericValue<T> &y) const {
            return x < y;
        }
    };

    class ValueRadix
    {
    public:
        uint64_t operator()(const LoadedValue<T> &v) const {
            return vespalib::convertForSort<T, true>::convert(v.getValue());
        }
    };
};


template <typename T>
void
sortLoadedByValue(SequentialReadModifyWriteVector<LoadedNumericValue<T>> & loaded);

template <typename T>
void
sortLoadedByDocId(SequentialReadModifyWriteVector<LoadedNumericValue<T>> & loaded);

}
