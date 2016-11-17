// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
#include "multi_value_mapping2.h"
#include "multi_value_mapping2.hpp"
#include <vespa/vespalib/stllike/string.h>
#include "multivalue.h"
#include "enumstorebase.h"
#include "attributevector.h"

LOG_SETUP(".searchlib.attribute.multivaluemapping2");

using search::multivalue::Value;
using search::multivalue::WeightedValue;

namespace search {
namespace attribute {

MultiValueMapping2Base::MultiValueMapping2Base(const GrowStrategy &gs,
                                               vespalib::GenerationHolder &genHolder)
    : _indices(gs, genHolder)
{
}

MultiValueMapping2Base::~MultiValueMapping2Base()
{
}

MultiValueMapping2Base::IndexCopyVector
MultiValueMapping2Base::getIndicesCopy(uint32_t size) const {
    assert(size <= _indices.size());
    return IndexCopyVector(&_indices[0], &_indices[0] + size);
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

template class MultiValueMapping2<Value<EnumStoreIndex>>;
template class MultiValueMapping2<WeightedValue<EnumStoreIndex>>;
template class MultiValueMapping2<Value<int8_t>>;
template class MultiValueMapping2<WeightedValue<int8_t>>;
template class MultiValueMapping2<Value<int16_t>>;
template class MultiValueMapping2<WeightedValue<int16_t>>;
template class MultiValueMapping2<Value<int32_t>>;
template class MultiValueMapping2<WeightedValue<int32_t>>;
template class MultiValueMapping2<Value<int64_t>>;
template class MultiValueMapping2<WeightedValue<int64_t>>;
template class MultiValueMapping2<Value<float>>;
template class MultiValueMapping2<WeightedValue<float>>;
template class MultiValueMapping2<Value<double>>;
template class MultiValueMapping2<WeightedValue<double>>;

} // namespace search::attribute
} // namespace search
