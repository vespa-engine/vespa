// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search
{


template <typename T, typename I>
template <typename V, class Saver>
uint32_t
MultiValueMappingT<T, I>::fillMapped(AttributeVector::ReaderBase &attrReader,
                                     uint64_t numValues,
                                     const V *map,
                                     size_t mapSize,
                                     Saver &saver,
                                     uint32_t numDocs,
                                     bool hasWeights)
{
    typedef AttributeVector::DocId DocId;
    Histogram capacityNeeded = this->getHistogram(attrReader);
    reset(numDocs, capacityNeeded);
    attrReader.rewind();
    std::vector<T> indices;
    uint64_t di = 0;
    uint32_t maxvc = 0;
    for (DocId doc = 0; doc < numDocs; ++doc) {
        indices.clear();
        uint32_t vc = attrReader.getNextValueCount();
        indices.reserve(vc);
        for (uint32_t vci = 0; vci < vc; ++vci, ++di) {
            uint32_t e = attrReader.getNextEnum();
            assert(e < mapSize);
            (void) mapSize;
            int32_t weight = hasWeights ? attrReader.getNextWeight() : 1;
            indices.push_back(T(map[e], weight));
            saver.save(e, doc, vci, weight);
        }
        if (maxvc < indices.size())
            maxvc = indices.size();
        set(doc, indices);
    }
    assert(di == numValues);
    (void) numValues;
    return maxvc;
}


} // namespace search

