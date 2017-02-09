// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "iterator_pack.h"
#include <vespa/searchlib/common/bitvector.h>

namespace search {
namespace queryeval {

std::unique_ptr<BitVector>
SearchIteratorPack::get_hits(uint32_t begin_id, uint32_t end_id) {
    if (_children.empty()) {
        return BitVector::create(begin_id, end_id);
    }

    BitVector::UP result = _children[0]->get_hits(begin_id);
    for (size_t i = 1; i < size(); ++i) {
        _children[i]->or_hits_into(*result, begin_id);
    }
    return result;
}

}
} // namespace search

