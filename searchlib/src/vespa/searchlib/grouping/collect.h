// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "groupref.h"
#include <vespa/searchlib/aggregation/group.h>
#include <vespa/vespalib/util/array.h>

namespace search::grouping {

class Collect
{
public:
    Collect(const Collect &) = delete;
    Collect & operator = (const Collect &) = delete;
protected:
    Collect(const aggregation::Group & protoType);
    ~Collect();
    void preFill(GroupRef gr, const aggregation::Group & r);
    void createCollectors(GroupRef gr);
    void collect(GroupRef group, uint32_t docId, double rank);
    void getCollectors(GroupRef ref, aggregation::Group & g) const;
    int cmpAggr(GroupRef a, GroupRef b) const {
        int diff(0);
        size_t aOff(getAggrBase(a));
        size_t bOff(getAggrBase(b));
        for(std::vector<SortInfo>::const_iterator it(_sortInfo.begin()), mt(_sortInfo.end()); (diff == 0) && (it != mt); it++) {
            diff = _aggregator[it->getIndex()].cmp(&_aggrBacking[aOff], &_aggrBacking[bOff]) * it->getSign();
        }
        return diff;
    }
    uint64_t radixAggrAsc(GroupRef gr) const {
        return _aggregator[_sortInfo[0].getIndex()].radixAsc(&_aggrBacking[getAggrBase(gr)]);
    }
    uint64_t radixAggrDesc(GroupRef gr) const {
        return _aggregator[_sortInfo[0].getIndex()].radixDesc(&_aggrBacking[getAggrBase(gr)]);
    }
    bool hasSpecifiedOrder() const { return ! _sortInfo.empty(); }
    bool isPrimarySortKeyAscending() const { return _sortInfo[0].getSign() >= 0; }
private:
    // Returns the byteoffset where aggregationresults for this group are stored.
    size_t getAggrBase(GroupRef gr) const { return _aggregatorSize*gr.getRef(); }
    // Return the aggregator with the corresponding id for the requested group.
    const expression::ResultNode & getAggrResult(uint32_t aggrId, GroupRef ref) const {
        return _aggregator[aggrId].getResult(&_aggrBacking[getAggrBase(ref.getRef())]);
    }

    /**
     * A ResultAccessor hides the dirty details for aggregating and accessing results
     * stored in flat memory elsewhere.
     * It keeps an offset that is added to get to memory storing the result.
     * It also keeps a scratch aggregator for doing the calculation. The 'warm' method, aggregate, does
     * r.swap(m); r.aggregate(); r.swap(m);
     * The extra incurred cost is dual swap, in exchange for avoiding the memory cost of virtual objects.
     * TODO: This are solutions planned to avoid the dual swaps. But so far they can be neglected as they do not occupy many cycles.
     */
    class ResultAccessor {
    public:
        ResultAccessor() : _bluePrint(NULL), _aggregator(NULL), _offset(0) { }
        ResultAccessor(const aggregation::AggregationResult & aggregator, size_t offset);
        void setResult(const expression::ResultNode & result, uint8_t * base) {
            result.encode(base+_offset);
        }
        const expression::ResultNode & getResult(expression::ResultNode & result, const uint8_t * base) const {
            result.decode(base+_offset);
            return result;
        }
        const expression::ResultNode & getResult(const uint8_t * base) const {
            _aggregator->getResult().decode(base+_offset);
            return _aggregator->getResult();
        }
        size_t getRawByteSize() const { return _aggregator->getResult().getRawByteSize(); }
        uint64_t radixAsc(const uint8_t * a) const { return _aggregator->getResult().radixAsc(a); }
        uint64_t radixDesc(const uint8_t * a) const { return _aggregator->getResult().radixDesc(a); }
        int cmp(const uint8_t * a, const uint8_t * b) const {
            return _aggregator->getResult().cmpMem(a, b);
        }
        void create(uint8_t * base);
        void destroy(uint8_t * base) { _aggregator->getResult().destroy(base+_offset); }
        void aggregate(uint8_t * base, uint32_t docId, double rank) {
            _aggregator->getResult().swap(base+_offset);
            _aggregator->aggregate(docId, rank);
            _aggregator->getResult().swap(base+_offset);
        }
    private:
        const aggregation::AggregationResult * _bluePrint;
        mutable vespalib::IdentifiablePtr<aggregation::AggregationResult> _aggregator;
        uint32_t       _offset;
    };
    using AggregatorBacking = vespalib::Array<uint8_t>;
    using ResultAccessorList = vespalib::Array<ResultAccessor>;
    class SortInfo {
    public:
        SortInfo() noexcept : _index(0), _sign(1) { }
        SortInfo(uint8_t index, int8_t sign) : _index(index), _sign(sign) { }
        uint8_t getIndex() const { return _index; }
        int8_t   getSign() const { return _sign; }
    private:
        uint8_t _index;  // Which index in the aggragators should be used for sorting this level.
        int8_t  _sign;   // And which way. positive number -> ascending, negative number descending.
    };
    size_t             _aggregatorSize;  // This is the bytesize required to store the aggrgate values per bucket.
    ResultAccessorList _aggregator;      // These are the accessors to use when accessing the results.
    AggregatorBacking  _aggrBacking;     // This is the storage for the accessors.
    std::vector<SortInfo> _sortInfo;     // Generated cheap sortInfo, to avoid accessing more complicated data.
};

}
