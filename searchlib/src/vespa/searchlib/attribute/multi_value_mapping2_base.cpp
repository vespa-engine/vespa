// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "multi_value_mapping2_base.h"
#include "attributevector.h"

namespace search {
namespace attribute {

MultiValueMapping2Base::MultiValueMapping2Base(const GrowStrategy &gs,
                                               vespalib::GenerationHolder &genHolder)
    : _indices(gs, genHolder),
      _totalValues(0u)
{
}

MultiValueMapping2Base::~MultiValueMapping2Base()
{
}

MultiValueMapping2Base::RefCopyVector
MultiValueMapping2Base::getRefCopy(uint32_t size) const {
    assert(size <= _indices.size());
    return RefCopyVector(&_indices[0], &_indices[0] + size);
}

void
MultiValueMapping2Base::addDoc(uint32_t & docId)
{
    uint32_t retval = _indices.size();
    _indices.push_back(EntryRef());
    docId = retval;
}

void
MultiValueMapping2Base::shrink(uint32_t docIdLimit)
{
    assert(docIdLimit < _indices.size());
    _indices.shrink(docIdLimit);
}

void
MultiValueMapping2Base::clearDocs(uint32_t lidLow, uint32_t lidLimit, AttributeVector &v)
{
    assert(lidLow <= lidLimit);
    assert(lidLimit <= v.getNumDocs());
    assert(lidLimit <= _indices.size());
    for (uint32_t lid = lidLow; lid < lidLimit; ++lid) {
        if (_indices[lid].valid()) {
            v.clearDoc(lid);
        }
    }
}

} // namespace search::attribute
} // namespace search
