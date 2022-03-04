// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchlib/queryeval/idiversifier.h>
#include <vespa/vespalib/datastore/entryref.h>

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

class DiversityFilter : public queryeval::IDiversifier {
public:
    DiversityFilter(size_t max_total) : _max_total(max_total) {}
    size_t getMaxTotal() const { return _max_total; }
    static std::unique_ptr<DiversityFilter>
    create(const IAttributeVector &diversity_attr, size_t wanted_hits,
           size_t max_per_group,size_t cutoff_max_groups, bool cutoff_strict);
protected:
    size_t _max_total;
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
void diversify_2(const DictRange &range_in, const PostingStore &posting, DiversityFilter & filter,
                 Result &result, std::vector<size_t> &fragments)
{

    DiversityRecorder<Result> recorder(filter, result);
    DictRange range(range_in);
    using DataType = typename PostingStore::DataType;
    using KeyDataType = typename PostingStore::KeyDataType;
    while (range.has_next() && (result.size() < filter.getMaxTotal())) {
        typename DictRange::Next dict_entry(range);
        posting.foreach_frozen(dict_entry.get().getData().load_acquire(),
                               [&](uint32_t key, const DataType &data)
                               { recorder.push_back(KeyDataType(key, data)); });
        if (fragments.back() < result.size()) {
            fragments.push_back(result.size());
        }
    }
}

template <typename DictItr, typename PostingStore, typename Result>
void diversify(bool forward, const DictItr &lower, const DictItr &upper, const PostingStore &posting, size_t wanted_hits,
               const IAttributeVector &diversity_attr, size_t max_per_group,
               size_t cutoff_max_groups, bool cutoff_strict,
               Result &array, std::vector<size_t> &fragments)
{
    auto filter = DiversityFilter::create(diversity_attr, wanted_hits, max_per_group, cutoff_max_groups, cutoff_strict);
    if (forward) {
        diversify_2(ForwardRange<DictItr>(lower, upper), posting, *filter, array, fragments);
    } else {
        diversify_2(ReverseRange<DictItr>(lower, upper), posting, *filter, array, fragments);
    }
}

template <typename PostingStore, typename Result>
void diversify_single(vespalib::datastore::EntryRef posting_idx, const PostingStore &posting, size_t wanted_hits,
               const IAttributeVector &diversity_attr, size_t max_per_group,
               size_t cutoff_max_groups, bool cutoff_strict,
               Result &result, std::vector<size_t> &fragments)
{
    auto filter = DiversityFilter::create(diversity_attr, wanted_hits, max_per_group, cutoff_max_groups, cutoff_strict);
    DiversityRecorder<Result> recorder(*filter, result);
    using DataType = typename PostingStore::DataType;
    using KeyDataType = typename PostingStore::KeyDataType;
    posting.foreach_frozen(posting_idx,
                           [&](uint32_t key, const DataType &data)
                           { recorder.push_back(KeyDataType(key, data)); });
    if (fragments.back() < result.size()) {
        fragments.push_back(result.size());
    }
}

}
