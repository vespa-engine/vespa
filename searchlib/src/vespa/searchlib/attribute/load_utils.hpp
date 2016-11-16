// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "load_utils.h"

namespace search {
namespace attribute {

template <class MvMapping, class Saver>
uint32_t
loadFromEnumeratedMultiValue(MvMapping &mapping,
                             AttributeVector::ReaderBase &attrReader,
                             vespalib::ConstArrayRef<typename MvMapping::MultiValueType::ValueType> map,
                             Saver saver)
{
    using MultiValueType = typename MvMapping::MultiValueType;
    std::vector<MultiValueType> indices;
    uint32_t numDocs = attrReader.getNumIdx() - 1;
    uint64_t numValues = attrReader.getNumValues();
    uint64_t enumCount = attrReader.getEnumCount();
    assert(numValues == enumCount);
    (void) enumCount;

    uint64_t di = 0;
    uint32_t maxvc = 0;
    for (uint32_t doc = 0; doc < numDocs; ++doc) {
        indices.clear();
        uint32_t vc = attrReader.getNextValueCount();
        indices.reserve(vc);
        for (uint32_t vci = 0; vci < vc; ++vci, ++di) {
            uint32_t e = attrReader.getNextEnum();
            assert(e < map.size());
            int32_t weight = MultiValueType::_hasWeight ? attrReader.getNextWeight() : 1;
            indices.emplace_back(map[e], weight);
            saver.save(e, doc, weight);
        }
        if (maxvc < indices.size()) {
            maxvc = indices.size();
        }
        mapping.set(doc, indices);
    }
    assert(di == numValues);
    (void) numValues;
    return maxvc;
}

template <class Vector, class Saver>
void
loadFromEnumeratedSingleValue(Vector &vector,
                              vespalib::GenerationHolder &genHolder,
                              AttributeVector::ReaderBase &attrReader,
                              vespalib::ConstArrayRef<typename Vector::ValueType> map,
                              Saver saver)
{
    uint32_t numDocs = attrReader.getEnumCount();
    genHolder.clearHoldLists();
    vector.reset();
    vector.unsafe_reserve(numDocs);
    for (uint32_t doc = 0; doc < numDocs; ++doc) {
        uint32_t e = attrReader.getNextEnum();
        assert(e < map.size());
        vector.push_back(map[e]);
        saver.save(e, doc, 1);
    }
}

} // namespace search::attribute
} // namespace search
