// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/sort.h>
#include <vespa/searchlib/util/fileutil.h>
#include <vespa/searchlib/util/foldedstringcompare.h>
#include <vespa/vespalib/text/utf8.h>
#include <vespa/vespalib/text/lowercase.h>
#include "loadedvalue.h"

namespace search
{

namespace attribute
{

/**
 * Temporary representation of enumerated attribute loaded from non-enumerated
 * save file (i.e. old save format).  For string data types.
 */

template <typename B>
struct RadixSortable : public B
{
    RadixSortable()
        : B(),
          _currRadix(NULL),
          _currRadixFolding(false)
    {
    }

    class ValueRadix
    {
    public:
        uint32_t
        operator ()(RadixSortable &x) const
        {
            vespalib::Utf8ReaderForZTS u8reader(x._currRadix);
            uint32_t val = u8reader.getChar();
            if (x._currRadixFolding) {
                if (val != 0) {
                    val = vespalib::LowerCase::convert(val);
                } else {
                    // switch to returning unfolded values
                    x._currRadix = x.getValue();
                    x._currRadixFolding = false;
                    val = 1;
                }
            }
            return val;
        }
    };

    class ValueCompare : public std::binary_function<B, B, bool>
    {
        FoldedStringCompare _compareHelper;
    public:
        bool
        operator()(const B &x, const B &y) const
        {
            return _compareHelper.compare(x.getValue(), y.getValue()) < 0;
        }
    };

    void
    prepareRadixSort()
    {
        _currRadix = this->getValue();
        _currRadixFolding = true;
    }
private:
    const char * _currRadix;
    bool _currRadixFolding;
};

typedef RadixSortable<LoadedValue<const char *> > LoadedStringValue;

typedef SequentialReadModifyWriteInterface<LoadedStringValue> LoadedStringVector;

typedef SequentialReadModifyWriteVector<LoadedStringValue>
LoadedStringVectorReal;


void
sortLoadedByValue(LoadedStringVectorReal &loaded);

void
sortLoadedByDocId(LoadedStringVectorReal &loaded);


} // namespace attribute

} // namespace search

