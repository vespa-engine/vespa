// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dfa_string_comparator.h"
#include <vespa/searchlib/util/foldedstringcompare.h>

namespace search::attribute {

DfaStringComparator::DfaStringComparator(const DataStoreType& data_store, const char* candidate)
    : ParentType(data_store, candidate)
{
}

bool
DfaStringComparator::less(const vespalib::datastore::EntryRef lhs, const vespalib::datastore::EntryRef rhs) const
{
    if (lhs.valid()) {
        if (rhs.valid()) {
            return FoldedStringCompare::compareFolded<true, true>(get(lhs), get(rhs)) < 0;
        } else {
            return FoldedStringCompare::compareFolded<true, false>(get(lhs), get(rhs)) < 0;
        }
    } else {
        if (rhs.valid()) {
            return FoldedStringCompare::compareFolded<false, true>(get(lhs), get(rhs)) < 0;
        } else {
            return FoldedStringCompare::compareFolded<false, false>(get(lhs), get(rhs)) < 0;
        }
    }
}

}
