// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "singleenumattribute.h"
#include "singlenumericattribute.h"
#include <vespa/vespalib/stllike/hash_map.h>

/**
 * This file contains low-level code used to implement diversified
 * limited attribute range searches. Terms on the form [;;100;foo;3]
 * are used to specify unbound range searches in an attribute that
 * produces a limited number of results while also ensuring
 * diversified results based on a secondary attribute.
 **/

namespace search::attribute::diversity {

template <typename ITR>
class ForwardRange
{
private:
    ITR _lower;
    ITR _upper;
public:
    class Next {
    private:
        ITR &_lower;
    public:
        Next(const Next &) = delete;
        explicit Next(ForwardRange &range) : _lower(range._lower) {}
        const ITR &get() const { return _lower; }
        ~Next() { ++_lower; }
    };
    ForwardRange(const ForwardRange &);
    ForwardRange(const ITR &lower, const ITR &upper);
    ~ForwardRange();
    bool has_next() const { return _lower != _upper; }
};

template <typename ITR>
class ReverseRange
{
private:
    ITR _lower;
    ITR _upper;
public:
    class Next {
    private:
        ITR &_upper;
    public:
        Next(const Next &) = delete;
        explicit Next(ReverseRange &range) : _upper(range._upper) { --_upper; }
        const ITR &get() const { return _upper; }
    };
    ReverseRange(const ReverseRange &);
    ReverseRange(const ITR &lower, const ITR &upper);
    ~ReverseRange();
    bool has_next() const { return _lower != _upper; }
};

template <typename T>
struct FetchNumberFast {
    const T * const attr;
    typedef typename T::LoadedValueType ValueType;
    FetchNumberFast(const IAttributeVector &attr_in) : attr(dynamic_cast<const T *>(&attr_in)) {}
    ValueType get(uint32_t docid) const { return attr->getFast(docid); }
    bool valid() const { return (attr != nullptr); }
};

struct FetchEnumFast {
    const SingleValueEnumAttributeBase * const attr;
    typedef uint32_t ValueType;
    FetchEnumFast(const IAttributeVector &attr_in) : attr(dynamic_cast<const SingleValueEnumAttributeBase *>(&attr_in)) {}
    ValueType get(uint32_t docid) const { return attr->getE(docid); }
    bool valid() const { return (attr != nullptr); }
};

struct FetchEnum {
    const IAttributeVector &attr;
    typedef uint32_t ValueType;
    FetchEnum(const IAttributeVector &attr_in) : attr(attr_in) {}
    ValueType get(uint32_t docid) const { return attr.getEnum(docid); }
};

struct FetchInteger {
    const IAttributeVector &attr;
    typedef int64_t ValueType;
    FetchInteger(const IAttributeVector &attr_in) : attr(attr_in) {}
    ValueType get(uint32_t docid) const { return attr.getInt(docid); }
};

struct FetchFloat {
    const IAttributeVector &attr;
    typedef double ValueType;
    FetchFloat(const IAttributeVector &attr_in) : attr(attr_in) {}
    ValueType get(uint32_t docid) const { return attr.getFloat(docid); }
};

class DiversityFilter {
public:
    DiversityFilter(size_t max_total) : _max_total(max_total) {}
    virtual ~DiversityFilter() {}
    virtual bool accepted(uint32_t docId) = 0;
    size_t getMaxTotal() const { return _max_total; }
protected:
    size_t _max_total;
};

template <typename Fetcher>
class DiversityFilterT final : public DiversityFilter {
private:
    size_t _total_count;
    const Fetcher &_diversity;
    size_t _max_per_group;
    size_t _cutoff_max_groups;
    bool   _cutoff_strict;

    typedef vespalib::hash_map<typename Fetcher::ValueType, uint32_t> Diversity;
    Diversity _seen;
public:
    DiversityFilterT(const Fetcher &diversity, size_t max_per_group,
                    size_t cutoff_max_groups, bool cutoff_strict, size_t max_total)
        : DiversityFilter(max_total), _total_count(0), _diversity(diversity), _max_per_group(max_per_group),
          _cutoff_max_groups(cutoff_max_groups), _cutoff_strict(cutoff_strict),
          _seen(std::min(cutoff_max_groups, 10000ul)*3)
    { }

    bool accepted(uint32_t docId) override;
private:
    bool add() {
        ++_total_count;
        return true;
    }
    bool conditional_add(uint32_t & group_count) {
        if (group_count  < _max_per_group) {
            ++group_count;
            add();
            return true;
        }
        return false;
    }
};

template <typename Result>
class DiversityRecorder {
private:
    DiversityFilter & _filter;
    Result &_result;
public:
    DiversityRecorder(DiversityFilter & filter, Result &result)
        : _filter(filter), _result(result)
    { }

    template <typename Item>
    void push_back(Item item) {
        if (_filter.accepted(item._key)) {
            _result.push_back(item);
        }
    }

};

template <typename DictRange, typename PostingStore, typename Result>
void diversify_3(const DictRange &range_in, const PostingStore &posting, DiversityFilter & filter,
                 Result &result, std::vector<size_t> &fragments)
{

    DiversityRecorder<Result> recorder(filter, result);
    DictRange range(range_in);
    using DataType = typename PostingStore::DataType;
    using KeyDataType = typename PostingStore::KeyDataType;
    while (range.has_next() && (result.size() < filter.getMaxTotal())) {
        typename DictRange::Next dict_entry(range);
        posting.foreach_frozen(dict_entry.get().getData(),
                               [&](uint32_t key, const DataType &data)
                               { recorder.push_back(KeyDataType(key, data)); });
        if (fragments.back() < result.size()) {
            fragments.push_back(result.size());
        }
    }
}

template <typename DictRange, typename PostingStore, typename Result>
void diversify_2(const DictRange &range_in, const PostingStore &posting, size_t wanted_hits,
                 const IAttributeVector &diversity_attr, size_t max_per_group,
                 size_t cutoff_max_groups, bool cutoff_strict,
                 Result &result, std::vector<size_t> &fragments)
{
    if (diversity_attr.hasEnum()) { // must handle enum first
        FetchEnumFast fastEnum(diversity_attr);
        if (fastEnum.valid()) {
            DiversityFilterT<FetchEnumFast> filter(fastEnum, max_per_group, cutoff_max_groups, cutoff_strict, wanted_hits);
            diversify_3(range_in, posting, filter, result, fragments);
        } else {
            DiversityFilterT<FetchEnum> filter(FetchEnum(diversity_attr), max_per_group, cutoff_max_groups, cutoff_strict, wanted_hits);
            diversify_3(range_in, posting, filter, result, fragments);
        }
    } else if (diversity_attr.isIntegerType()) {
        using FetchInt32Fast = FetchNumberFast<SingleValueNumericAttribute<IntegerAttributeTemplate<int32_t> > >;
        using FetchInt64Fast = FetchNumberFast<SingleValueNumericAttribute<IntegerAttributeTemplate<int64_t> > >;

        FetchInt32Fast fastInt32(diversity_attr);
        FetchInt64Fast fastInt64(diversity_attr);
        if (fastInt32.valid()) {
            DiversityFilterT<FetchInt32Fast> filter(fastInt32, max_per_group, cutoff_max_groups, cutoff_strict, wanted_hits);
            diversify_3(range_in, posting, filter, result, fragments);
        } else if (fastInt64.valid()) {
            DiversityFilterT<FetchInt64Fast> filter(fastInt64, max_per_group, cutoff_max_groups, cutoff_strict, wanted_hits);
            diversify_3(range_in, posting, filter, result, fragments);
        } else {
            DiversityFilterT<FetchInteger> filter(FetchInteger(diversity_attr), max_per_group, cutoff_max_groups, cutoff_strict, wanted_hits);
            diversify_3(range_in, posting, filter, result, fragments);
        }
    } else if (diversity_attr.isFloatingPointType()) {
        using FetchFloatFast = FetchNumberFast<SingleValueNumericAttribute<FloatingPointAttributeTemplate<float> > >;
        using FetchDoubleFast = FetchNumberFast<SingleValueNumericAttribute<FloatingPointAttributeTemplate<double> > >;
        FetchFloatFast fastFloat(diversity_attr);
        FetchDoubleFast fastDouble(diversity_attr);
        if (fastFloat.valid()) {
            DiversityFilterT<FetchFloatFast> filter(fastFloat, max_per_group, cutoff_max_groups, cutoff_strict, wanted_hits);
            diversify_3(range_in, posting, filter, result, fragments);
        } else if (fastDouble.valid()) {
            DiversityFilterT<FetchDoubleFast> filter(fastDouble, max_per_group, cutoff_max_groups, cutoff_strict, wanted_hits);
            diversify_3(range_in, posting, filter, result, fragments);
        } else {
            DiversityFilterT<FetchFloat> filter(FetchFloat(diversity_attr), max_per_group, cutoff_max_groups, cutoff_strict, wanted_hits);
            diversify_3(range_in, posting, filter, result, fragments);
        }
    }
}

template <typename DictItr, typename PostingStore, typename Result>
void diversify(bool forward, const DictItr &lower, const DictItr &upper, const PostingStore &posting, size_t wanted_hits,
               const IAttributeVector &diversity_attr, size_t max_per_group,
               size_t cutoff_max_groups, bool cutoff_strict,
               Result &array, std::vector<size_t> &fragments)
{
    if (forward) {
        diversify_2(ForwardRange<DictItr>(lower, upper), posting, wanted_hits,
                    diversity_attr, max_per_group, cutoff_max_groups, cutoff_strict, array, fragments);
    } else {
        diversify_2(ReverseRange<DictItr>(lower, upper), posting, wanted_hits,
                    diversity_attr, max_per_group, cutoff_max_groups, cutoff_strict, array, fragments);
    }
}

}
