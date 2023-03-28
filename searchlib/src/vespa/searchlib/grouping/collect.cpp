// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "collect.h"
#include <vespa/vespalib/util/array.hpp>
#include <cassert>

using namespace search::expression;
using namespace search::aggregation;

namespace search::grouping {

Collect::ResultAccessor::ResultAccessor(const AggregationResult & aggregator, size_t offset) :
    _bluePrint(&aggregator),
    _aggregator(_bluePrint->clone()),
    _offset(offset)
{
}

void Collect::ResultAccessor::create(uint8_t * base)
{
    _aggregator->getResult().create(base+_offset);
    _bluePrint->getResult()->encode(base+_offset);
}

Collect::Collect(const Group & gp) :
    _aggregatorSize(0),
    _aggregator(),
    _aggrBacking()
{
    _aggregator.reserve(gp.getAggrSize());
    for (size_t i(0); i < gp.getAggrSize(); i++) {
        ResultAccessor accessor(const_cast<AggregationResult &>(gp.getAggregationResult(i)), _aggregatorSize);
        _aggregator.push_back(accessor);
        assert(accessor.getRawByteSize() > 0);
        _aggregatorSize += accessor.getRawByteSize();
    }
    _sortInfo.resize(gp.getOrderBySize());
    for(size_t i(0); i < _sortInfo.size(); i++) {
        const uint32_t index = std::abs(gp.getOrderBy(i)) - 1;
        const uint32_t z(gp.getExpr(index));
        _sortInfo[i] = SortInfo(z, gp.getOrderBy(i));
    }
}

Collect::~Collect()
{
    if (_aggregatorSize > 0) {
        assert((_aggrBacking.size() % _aggregatorSize) == 0);
        for (size_t i(0), m(_aggrBacking.size()/_aggregatorSize); i < m; i++) {
            uint8_t * base(&_aggrBacking[ i * _aggregatorSize]);
            for (size_t j(0), k(_aggregator.size()); j < k; j++) {
                ResultAccessor & r = _aggregator[j];
                r.destroy(base);
            }
        }
    }
}

void
Collect::getCollectors(GroupRef ref, Group & g) const
{
    size_t offset(getAggrBase(ref));
    if (offset < _aggrBacking.size()) {
        const uint8_t * base(&_aggrBacking[offset]);
        for (size_t i(0), m(_aggregator.size()); i < m; i++) {
            const ResultAccessor & r = _aggregator[i];
            r.getResult(g.getAggregationResult(i).getResult(), base);
            g.getAggregationResult(i).postMerge();
        }
    }
}

void
Collect::collect(GroupRef gr, uint32_t docId, double rank)
{
    uint8_t * base(&_aggrBacking[getAggrBase(gr)]);
    for (size_t i(0), m(_aggregator.size()); i < m; i++) {
        _aggregator[i].aggregate(base, docId, rank);
    }
}

void
Collect::createCollectors(GroupRef gr)
{
    size_t offset(getAggrBase(gr));
    if (offset == _aggrBacking.size()) {
        _aggrBacking.resize(getAggrBase(GroupRef(gr.getRef() + 1)));
        uint8_t * base(&_aggrBacking[offset]);
        for (size_t i(0), m(_aggregator.size()); i < m; i++) {
            ResultAccessor & r = _aggregator[i];
            r.create(base);
        }
    }
}

void
Collect::preFill(GroupRef gr, const Group & g)
{
    if (gr.valid()) {
        size_t offset(getAggrBase(gr));
        uint8_t * base(&_aggrBacking[offset]);
        for (size_t i(0), m(_aggregator.size()); i < m; i++) {
            ResultAccessor & r = _aggregator[i];
            r.setResult(*g.getAggregationResult(i).getResult(), base);
        }
    }
}

}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_grouping_collect() {}
