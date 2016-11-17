// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/datastore/entryref.h>
#include <vespa/searchlib/common/rcuvector.h>

namespace search {

class AttributeVector;

namespace attribute {

/**
 * Base class for mapping from from document id to an array of values.
 */
class MultiValueMapping2Base
{
public:
    using EntryRef = datastore::EntryRef;
    using RefVector = RcuVectorBase<EntryRef>;

protected:
    RefVector _indices;
    size_t    _totalValues;

    MultiValueMapping2Base(const GrowStrategy &gs, vespalib::GenerationHolder &genHolder);
    virtual ~MultiValueMapping2Base();

    void updateValueCount(size_t oldValues, size_t newValues) {
        _totalValues += newValues - oldValues;
    }
public:
    using RefCopyVector = vespalib::Array<EntryRef>;

    virtual MemoryUsage getMemoryUsage() const = 0;
    size_t getTotalValueCnt() const { return _totalValues; }
    RefCopyVector getRefCopy(uint32_t size) const;

    void addDoc(uint32_t &docId);
    void shrink(uint32_t docidLimit);
    void clearDocs(uint32_t lidLow, uint32_t lidLimit, AttributeVector &v);
    uint32_t size() const { return _indices.size(); }

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
    static size_t maxValues() { return 0; }
};

} // namespace search::attribute
} // namespace search
