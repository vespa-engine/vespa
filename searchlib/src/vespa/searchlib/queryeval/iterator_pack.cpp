// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "iterator_pack.h"
#include <vespa/searchlib/common/bitvector.h>

namespace search {
namespace queryeval {

std::unique_ptr<BitVector>
SearchIteratorPack::get_hits(uint32_t begin_id, uint32_t end_id) {

    BitVector::UP result = SearchIterator::orChildren(_children, begin_id);
    if (! result ) {
        result = BitVector::create(begin_id, end_id);
    }
    return result;
}

void
SearchIteratorPack::or_hits_into(BitVector &result, uint32_t begin_id) {
    BitVector::UP dirty(&result);
    SearchIterator::orChildren(dirty, _children, begin_id);
    dirty.release(); // Yes I know, dirty...
}


}
} // namespace search

