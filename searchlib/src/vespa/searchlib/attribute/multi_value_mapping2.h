// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/datastore/entryref.h>
#include <vespa/searchlib/common/rcuvector.h>
#include <vespa/searchlib/datastore/array_store.h>
#include "address_space.h"

namespace search {

class AttributeVector;

namespace attribute {

/**
 * Class for mapping from from document id to an array of values.
 */
template <typename EntryT, typename RefT = datastore::EntryRefT<17> >
class MultiValueMapping2
{
public:
    using MultiValueType = EntryT;
private:
    using EntryRef = datastore::EntryRef;
    using IndexVector = RcuVectorBase<EntryRef>;
    using ArrayStore = datastore::ArrayStore<EntryT, RefT>;
    using generation_t = vespalib::GenerationHandler::generation_t;
    using ConstArrayRef = vespalib::ConstArrayRef<EntryT>;

    ArrayStore _store;
    IndexVector _indices;
public:
    MultiValueMapping2(uint32_t maxSmallArraySize,
                       const GrowStrategy &gs = GrowStrategy());
    ~MultiValueMapping2();
    ConstArrayRef get(uint32_t docId) const { return _store.get(_indices[docId]); }
    ConstArrayRef getDataForIdx(EntryRef idx) const { return _store.get(idx); }
    void set(uint32_t docId, ConstArrayRef values);

    // replace is generally unsafe and should only be used when
    // compacting enum store (replacing old enum index with updated enum index)
    void replace(uint32_t docId, ConstArrayRef values);

    // Pass on hold list management to underlying store
    void transferHoldLists(generation_t generation) { _store.transferHoldLists(generation); }
    void trimHoldLists(generation_t firstUsed) { _store.trimHoldLists(firstUsed); }
    template <class Reader>
    void prepareLoadFromMultiValue(Reader &) { }

    void compactWorst();

    // Following methods are not yet properly implemented.
    AddressSpace getAddressSpaceUsage() const { return AddressSpace(0, 0); }
    MemoryUsage getMemoryUsage() const { return MemoryUsage(); }
    size_t getTotalValueCnt() const { return 0; }
    void clearDocs(uint32_t lidLow, uint32_t lidLimit, AttributeVector &v) {
        (void) lidLow;
        (void) lidLimit;
        (void) v;
    }
    void shrinkKeys(uint32_t newSize) { (void) newSize; }
    void addKey(uint32_t &docId) {
        uint32_t oldVal = _indices.size();
        _indices.push_back(EntryRef());
        docId = oldVal;
    }

    // Mockups to temporarily silence code written for old multivalue mapping
    class Histogram
    {
    private:
        using HistogramM = std::vector<size_t>;
    public:
        using const_iterator = HistogramM::const_iterator;
        Histogram() : _histogram(1) { }
        size_t & operator [] (uint32_t) { return _histogram[0]; }
        const_iterator begin() const { return _histogram.begin(); }
        const_iterator   end() const { return _histogram.end(); }
    private:
        HistogramM _histogram;
    };
    Histogram getEmptyHistogram() const { return Histogram(); }
    bool enoughCapacity(const Histogram &) { return true; }
    void performCompaction(Histogram &) { }
    static size_t maxValues() { return 0; }
};

} // namespace search::attribute
} // namespace search
