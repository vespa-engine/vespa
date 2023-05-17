// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "load_utils.h"
#include "attributevector.h"
#include <vespa/searchcommon/attribute/multivalue.h>
#include <cassert>

namespace search::attribute {

template <class MvMapping, class Saver>
uint32_t
loadFromEnumeratedMultiValue(MvMapping & mapping,
                             ReaderBase & attrReader,
                             vespalib::ConstArrayRef<atomic_utils::NonAtomicValue_t<multivalue::ValueType_t<typename MvMapping::MultiValueType>>> enumValueToValueMap,
                             vespalib::ConstArrayRef<uint32_t> enum_value_remapping,
                             Saver saver)
{
    mapping.prepareLoadFromMultiValue();
    using MultiValueType = typename MvMapping::MultiValueType;
    using ValueType = multivalue::ValueType_t<MultiValueType>;
    using NonAtomicValueType = atomic_utils::NonAtomicValue_t<ValueType>;
    std::vector<MultiValueType> indices;
    uint32_t numDocs = attrReader.getNumIdx() - 1;
    uint64_t numValues = attrReader.getEnumCount();

    uint64_t totalValueCount = 0;
    uint32_t maxValueCount = 0;
    for (uint32_t doc = 0; doc < numDocs; ++doc) {
        indices.clear();
        uint32_t valueCount = attrReader.getNextValueCount();
        totalValueCount += valueCount;
        indices.reserve(valueCount);
        for (uint32_t vci = 0; vci < valueCount; ++vci) {
            uint32_t enumValue = attrReader.getNextEnum();
            assert(enumValue < enumValueToValueMap.size());
            if (!enum_value_remapping.empty()) {
                enumValue = enum_value_remapping[enumValue];
            }
            int32_t weight = multivalue::is_WeightedValue_v<MultiValueType> ? attrReader.getNextWeight() : 1;
            if constexpr (std::is_same_v<ValueType, NonAtomicValueType>) {
                indices.emplace_back(multivalue::ValueBuilder<MultiValueType>::build(enumValueToValueMap[enumValue], weight));
            } else {
                indices.emplace_back(multivalue::ValueBuilder<MultiValueType>::build(ValueType(enumValueToValueMap[enumValue]), weight));
            }
            saver.save(enumValue, doc, weight);
        }
        if (maxValueCount < indices.size()) {
            maxValueCount = indices.size();
        }
        mapping.set(doc, indices);
    }
    assert(totalValueCount == numValues);
    mapping.doneLoadFromMultiValue();
    (void) numValues;
    return maxValueCount;
}

template <class Vector, class Saver>
void
loadFromEnumeratedSingleValue(Vector &vector,
                              vespalib::GenerationHolder &genHolder,
                              ReaderBase &attrReader,
                              vespalib::ConstArrayRef<atomic_utils::NonAtomicValue_t<typename Vector::ValueType>> enumValueToValueMap,
                              vespalib::ConstArrayRef<uint32_t> enum_value_remapping,
                              Saver saver)
{
    using ValueType = typename Vector::ValueType;
    using NonAtomicValueType = atomic_utils::NonAtomicValue_t<ValueType>;
    uint32_t numDocs = attrReader.getEnumCount();
    genHolder.reclaim_all();
    vector.reset();
    vector.unsafe_reserve(numDocs);
    for (uint32_t doc = 0; doc < numDocs; ++doc) {
        uint32_t enumValue = attrReader.getNextEnum();
        assert(enumValue < enumValueToValueMap.size());
        if (!enum_value_remapping.empty()) {
            enumValue = enum_value_remapping[enumValue];
        }
        if constexpr (std::is_same_v<ValueType, NonAtomicValueType>) {
            vector.push_back(enumValueToValueMap[enumValue]);
        } else {
            vector.push_back(ValueType(enumValueToValueMap[enumValue]));
        }
        saver.save(enumValue, doc, 1);
    }
}

}
