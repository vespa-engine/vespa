// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/basictype.h>
#include <vespa/searchcommon/attribute/collectiontype.h>
#include <vespa/searchcommon/common/growstrategy.h>
#include <vespa/searchcommon/common/compaction_strategy.h>
#include <vespa/eval/eval/value_type.h>

namespace search {
namespace attribute {

class Config
{
public:
    Config();

    Config(BasicType bt,
           CollectionType ct = CollectionType::SINGLE,
           bool fastSearch_ = false,
           bool huge_ = false);

    BasicType basicType()                 const { return _basicType; }
    CollectionType collectionType()       const { return _type; }
    bool fastSearch()                     const { return _fastSearch; }
    bool huge()                           const { return _huge; }
    uint32_t arity()                      const { return _arity; }
    int64_t lower_bound()                 const { return _lower_bound; }
    int64_t upper_bound()                 const { return _upper_bound; }
    double dense_posting_list_threshold() const { return _dense_posting_list_threshold; }
    vespalib::eval::ValueType tensorType() const { return _tensorType; }

    /**
     * Check if attribute posting list can consist of a bitvector in
     * addition to (or instead of) a btree. 
     */
    bool
    getEnableBitVectors(void) const
    {
        return _enableBitVectors;
    }

    /**
     * Check if attribute posting list can consist of only a bitvector with
     * no corresponding btree.
     */
    bool
    getEnableOnlyBitVector(void) const
    {
        return _enableOnlyBitVector;
    }

    bool
    getIsFilter(void) const
    {
        return _isFilter;
    }

    /**
     * Check if this attribute should be fast accessible at all times.
     * If so, attribute is kept in memory also for non-searchable documents.
     */
    bool fastAccess() const { return _fastAccess; }

    const GrowStrategy & getGrowStrategy() const { return _growStrategy; }
    const CompactionStrategy &getCompactionStrategy() const { return _compactionStrategy; }
    void setHuge(bool v)                         { _huge = v; }
    void setFastSearch(bool v)                   { _fastSearch = v; }
    void setArity(uint32_t v)                    { _arity = v; }
    void setBounds(int64_t lower, int64_t upper) { _lower_bound = lower;
                                                   _upper_bound = upper; }
    void setDensePostingListThreshold(double v)  { _dense_posting_list_threshold = v; }
    void setTensorType(const vespalib::eval::ValueType &tensorType_in) {
        _tensorType = tensorType_in;
    }

    /**
     * Enable attribute posting list to consist of a bitvector in
     * addition to (or instead of) a btree. 
     */
    void
    setEnableBitVectors(bool enableBitVectors)
    {
        _enableBitVectors = enableBitVectors;
    }

    /**
     * Enable attribute posting list to consist of only a bitvector with
     * no corresponding btree. Some information degradation might occur when
     * document frequency goes down, since recreated btree representation
     * will then have lost weight information.
     */
    void
    setEnableOnlyBitVector(bool enableOnlyBitVector)
    {
        _enableOnlyBitVector = enableOnlyBitVector;
    }

    /**
     * Hide weight information when searching in attributes.
     */
    void
    setIsFilter(bool isFilter)
    {
        _isFilter = isFilter;
    }

    void setFastAccess(bool v) { _fastAccess = v; }
    Config & setGrowStrategy(const GrowStrategy &gs) { _growStrategy = gs; return *this; }
    Config &setCompactionStrategy(const CompactionStrategy &compactionStrategy) { _compactionStrategy = compactionStrategy; return *this; }
    bool operator!=(const Config &b) const { return !(operator==(b)); }

    bool
    operator==(const Config &b) const
    {
        return _basicType == b._basicType &&
               _type == b._type &&
               _huge == b._huge &&
               _fastSearch == b._fastSearch &&
               _enableBitVectors == b._enableBitVectors &&
               _enableOnlyBitVector == b._enableOnlyBitVector &&
               _isFilter == b._isFilter &&
               _fastAccess == b._fastAccess &&
               _growStrategy == b._growStrategy &&
               _compactionStrategy == b._compactionStrategy &&
               _arity == b._arity &&
               _lower_bound == b._lower_bound &&
               _upper_bound == b._upper_bound &&
               _dense_posting_list_threshold == b._dense_posting_list_threshold &&
            (_basicType.type() != BasicType::Type::TENSOR ||
             _tensorType == b._tensorType);
    }

private:
    BasicType      _basicType;
    CollectionType _type;
    bool           _fastSearch;
    bool           _huge;
    bool           _enableBitVectors;
    bool           _enableOnlyBitVector;
    bool           _isFilter;
    bool           _fastAccess;
    GrowStrategy   _growStrategy;
    CompactionStrategy _compactionStrategy;
    uint32_t       _arity;
    int64_t        _lower_bound;
    int64_t        _upper_bound;
    double         _dense_posting_list_threshold;
    vespalib::eval::ValueType _tensorType;
};
}  // namespace attribute
}  // namespace search

